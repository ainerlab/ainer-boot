#!/usr/bin/env bash
# 运行时装配门禁（docs/conventions.md §11、§12）：
#
#   1. Dockerfile 的 COPY 集合必须覆盖全部 reactor 模块（根 pom 与嵌套 pom 的 <module> 树）：
#      - pom 阶段（dependency:go-offline 依赖预热层）必须逐个 COPY 模块的 pom.xml；
#      - 源码阶段必须 COPY 到每个模块目录（或它的祖先目录）。
#      2026-09-11 的缺陷是 Dockerfile 停在 11 个模块，新增 7 个模块后从未同步，任何
#      docker build 都会在 Maven 项目加载阶段失败，而 CI 只跑 docker info 从不真构建。
#   2. 「声明了但实际不生效」的调度：仓内存在 @Scheduled 时，必须存在真正生效的
#      @EnableScheduling（main 源码，且没有被无关的 @ConditionalOnProperty 门控）。
#      2026-09-11 的缺陷是 @EnableScheduling 只挂在一个默认关闭的业务开关配置上，
#      默认部署下通知投递引擎永不运行、记录永远停在 PENDING 且没有任何报错。
#
# 退出码：0 = 通过；1 = 命中违规（输出 文件:行）；2 = 脚本自身用法/环境错误。
#
# TODO(cache-gate): 「@Cacheable 存在 ⇒ @EnableCaching 存在」的同类检查**暂不在本 PR 加入**，
#   它依赖并行分支 codex/adr-0039-cache-and-lock-reality 先合入（该分支负责缓存/锁的真实生效
#   装配）；在该分支合入前加入会让本 PR 变红。合入后在此处补上第三条检查。
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

[[ -f "$boot_root/Dockerfile" ]] || {
  echo "[ainer-runtime-wiring] ERROR: Dockerfile not found: $boot_root/Dockerfile" >&2
  exit 2
}

status=0
python3 - "$boot_root" <<'PY' || status=$?
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(sys.argv[1])
dockerfile = root / "Dockerfile"
POM_NS = "{http://maven.apache.org/POM/4.0.0}"
violations = []


def violation(location, message):
    violations.append(f"[ainer-runtime-wiring] VIOLATION: {location}: {message}")


# ---- reactor 模块树（根 pom 递归 <module>，路径相对仓库根）------------------
def submodules(pom):
    """pom 为相对仓库根的 pom.xml 路径；返回其 <module> 的相对路径。"""
    if not (root / pom).is_file():
        return []
    modules = ET.parse(root / pom).getroot().find(f"{POM_NS}modules")
    if modules is None:
        return []
    return [
        (pom.parent / child.text.strip())
        for child in modules
        if child.text and child.text.strip()
    ]


modules = set()
queue = [Path(".")]
while queue:
    current = queue.pop(0)
    if current.as_posix() in modules:
        continue
    modules.add(current.as_posix())
    queue.extend(submodules(current / "pom.xml"))
modules.discard(".")

if not modules:
    print("[ainer-runtime-wiring] ERROR: cannot parse <module> tree from pom.xml", file=sys.stderr)
    sys.exit(2)


# ---- Dockerfile COPY 语句分组（连续 COPY 视为一个阶段块）--------------------
blocks = []
current = None
for lineno, raw in enumerate(dockerfile.read_text(encoding="utf-8").splitlines(), start=1):
    line = raw.strip()
    if line.startswith("COPY "):
        tokens = [token for token in line.split()[1:] if not token.startswith("--")]
        if len(tokens) < 2:
            violation(f"Dockerfile:{lineno}", f"COPY 语句缺少源/目标：{line}")
            continue
        if current is None:
            current = {"line": lineno, "sources": []}
        current["sources"].extend(tokens[:-1])
    elif line and not line.startswith("#"):
        if current is not None:
            blocks.append(current)
            current = None
if current is not None:
    blocks.append(current)

if not blocks:
    print("[ainer-runtime-wiring] ERROR: Dockerfile has no COPY statement", file=sys.stderr)
    sys.exit(2)

pom_block = next(
    (block for block in blocks if any(source.rstrip("/") == "pom.xml" for source in block["sources"])),
    None,
)
source_block = max(
    (block for block in blocks if block is not pom_block),
    key=lambda block: sum(1 for source in block["sources"] if source.endswith("/")),
    default=None,
)
pom_anchor = f"Dockerfile:{pom_block['line']}" if pom_block else "Dockerfile:1"
source_anchor = f"Dockerfile:{source_block['line']}" if source_block else pom_anchor

pom_sources = {source.rstrip("/") for block in blocks for source in block["sources"]}
dir_sources = {source.rstrip("/") for block in blocks for source in block["sources"] if source.endswith("/")}
all_sources = {source.rstrip("/") for block in blocks for source in block["sources"]}

if "pom.xml" not in all_sources:
    violation(pom_anchor, "COPY 缺少根 pom.xml（Maven 项目加载必需）")

for module in sorted(modules):
    if f"{module}/pom.xml" not in pom_sources:
        violation(
            pom_anchor,
            f"COPY 缺少 reactor 模块 pom：{module}/pom.xml（根 pom <module> 树要求，新增模块时必须同步）",
        )
    # 只有带源码的模块才需要目录 COPY（ainer-dependencies 等 pom-only 模块只需 pom.xml）
    if not (root / module / "src").is_dir():
        continue
    ancestors = [module] + [str(parent).replace("\\", "/") for parent in Path(module).parents if str(parent) != "."]
    if not any(ancestor in dir_sources for ancestor in ancestors):
        violation(
            source_anchor,
            f"COPY 缺少 reactor 模块源码目录：{module}/（或其祖先目录）",
        )


# ---- Maven Wrapper 分发格式必须与 Docker 构建环境一致 ----------------------
# mvnw 在缺少 unzip 时会静默把 distributionUrl 换成 .tar.gz，而 maven-wrapper.properties 只
# 固定了 .zip 的 distributionSha256Sum → 容器内校验必然失败（宿主机 macOS 有 unzip，本地
# ./mvnw 正常，所以只有真跑 docker build 才会暴露）。镜像里装了 unzip 才算一致。
wrapper_properties = root / ".mvn/wrapper/maven-wrapper.properties"
if wrapper_properties.is_file():
    wrapper_text = wrapper_properties.read_text(encoding="utf-8", errors="ignore")
    distribution_url = re.search(r"^distributionUrl=(.+)$", wrapper_text, re.MULTILINE)
    checksum_pinned = re.search(r"^distributionSha256Sum=\S+", wrapper_text, re.MULTILINE)
    if distribution_url and checksum_pinned and distribution_url.group(1).strip().endswith(".zip"):
        dockerfile_text = dockerfile.read_text(encoding="utf-8", errors="ignore")
        if not re.search(r"apt-get install[^\n]*unzip", dockerfile_text):
            violation(
                "Dockerfile:1",
                "maven-wrapper.properties 固定的是 .zip 分发包校验和，但 Dockerfile 未安装 unzip："
                "mvnw 在容器内会静默改用 .tar.gz，distributionSha256Sum 校验必然失败",
            )


# ---- 产物路径不得依赖 Maven 输出的版本字符串 -------------------------------
# Maven 4 的 `help:evaluate -Dexpression=project.version -DforceStdout` 输出是
# `[INFO] [stdout] 0.1.0-SNAPSHOT`（Maven 3 才是裸值），用它拼 `target/<module>-<version>.jar`
# 会得到不存在的路径，构建在最后一步失败。按产物名定位 JAR 才与 Maven 版本无关。
dockerfile_text = dockerfile.read_text(encoding="utf-8", errors="ignore")
instruction_line = 0
instruction = []
for lineno, raw in enumerate(dockerfile_text.splitlines(), start=1):
    stripped = raw.rstrip()
    is_comment = stripped.lstrip().startswith("#")
    if not instruction:
        instruction_line = lineno
    if not is_comment:
        instruction.append(stripped.rstrip("\\").strip())
    if stripped.endswith("\\"):
        continue
    joined = " ".join(instruction)
    if "help:evaluate" in joined and "forceStdout" in joined:
        violation(
            f"Dockerfile:{instruction_line}",
            "Dockerfile 用 help:evaluate -DforceStdout 解析版本拼接产物路径："
            "Maven 4 输出带 '[INFO] [stdout] ' 前缀，路径必然不存在（改为按产物名定位 JAR）",
        )
    instruction = []


# ---- @Scheduled ⇒ 生效的 @EnableScheduling --------------------------------
def java_files(pattern):
    for path in sorted(root.rglob("*.java")):
        if "/target/" in path.as_posix():
            continue
        if re.search(pattern, path.read_text(encoding="utf-8", errors="ignore"), re.MULTILINE):
            yield path


scheduled = []
for path in java_files(r"^\s*@Scheduled\b"):
    text = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    for lineno, line in enumerate(text, start=1):
        if re.match(r"\s*@Scheduled\b", line):
            scheduled.append((path.relative_to(root).as_posix(), lineno))

if scheduled:
    enable_files = [
        path
        for path in java_files(r"^\s*@EnableScheduling\b")
        if "/src/main/java/" in path.as_posix()
    ]
    if not enable_files:
        for path, lineno in scheduled:
            violation(
                f"{path}:{lineno}",
                "@Scheduled 存在但 main 源码中没有 @EnableScheduling：调度器不会注册，方法永不执行",
            )
    else:
        # 生效性之二：自动装配类必须登记进 AutoConfiguration.imports，否则类存在但从不注册。
        imports_registrations = set()
        for imports_file in root.rglob("org.springframework.boot.autoconfigure.AutoConfiguration.imports"):
            if "/target/" in imports_file.as_posix():
                continue
            imports_registrations.update(
                line.strip()
                for line in imports_file.read_text(encoding="utf-8", errors="ignore").splitlines()
                if line.strip() and not line.strip().startswith("#")
            )
        autoconfig_enablers = []
        for path in enable_files:
            lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
            class_line = next(
                (lineno for lineno, line in enumerate(lines, start=1)
                 if re.match(r"\s*(public\s+)?(final\s+)?class\s+\w+AutoConfiguration\b", line)),
                None,
            )
            if class_line is None:
                continue
            package_line = next(
                (line.strip() for line in lines if line.strip().startswith("package ")),
                "package ;",
            )
            package = package_line[len("package "):].rstrip(";").strip()
            class_name = re.search(r"class\s+(\w+)", lines[class_line - 1]).group(1)
            autoconfig_enablers.append(
                (path.relative_to(root).as_posix(), class_line, f"{package}.{class_name}")
            )
        if autoconfig_enablers and not any(
            fqcn in imports_registrations for _, _, fqcn in autoconfig_enablers
        ):
            for path, lineno, fqcn in autoconfig_enablers:
                violation(
                    f"{path}:{lineno}",
                    f"{fqcn} 未登记进任何 META-INF/spring/org.springframework.boot.autoconfigure."
                    "AutoConfiguration.imports：类存在但应用启动时不会注册，调度仍然不生效",
                )

        for path in enable_files:
            lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
            conditional_line = next(
                (lineno for lineno, line in enumerate(lines, start=1) if "@ConditionalOnProperty" in line),
                None,
            )
            if conditional_line is None:
                continue
            if "matchIfMissing" in "\n".join(lines) and re.search(r"matchIfMissing\s*=\s*true", "\n".join(lines)):
                continue
            violation(
                f"{path.relative_to(root).as_posix()}:{conditional_line}",
                "@EnableScheduling 被 @ConditionalOnProperty 门控且未声明 matchIfMissing=true："
                "开关关闭时全局调度静默失效",
            )

if violations:
    for message in violations:
        print(message, file=sys.stderr)
    print(
        f"[ainer-runtime-wiring] 失败：{len(violations)} 处违规"
        "（规则见 docs/conventions.md §11，Dockerfile 维护约定见 Dockerfile 头部注释）",
        file=sys.stderr,
    )
    sys.exit(1)

print(
    f"[ainer-runtime-wiring] Dockerfile COPY 覆盖 {len(modules)} 个 reactor 模块；"
    f"{len(scheduled)} 处 @Scheduled 均有生效的 @EnableScheduling"
)
PY

exit "$status"
