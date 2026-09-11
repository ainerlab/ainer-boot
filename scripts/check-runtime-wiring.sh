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
#   3. 「@Cacheable / @CacheEvict / @CachePut 存在 ⇒ 存在真正生效的 @EnableCaching」：
#      2026-09-11 的缺陷是字典与配置模块真在用缓存注解，但全仓没有任何生产 @EnableCaching，
#      注解全部是死注解（Spring Boot 不会替你打开缓存）。
#   4. 「每个 fixedDelay/fixedRate 型 @Scheduled 必须声明首次执行延迟」：
#      2026-09-11 的缺陷是投递引擎未声明 initialDelay，Spring 在上下文刷新后立即执行一次，
#      那次"启动即投递"抢先领取了记录并写入租约，与测试里手动驱动的投递争抢同一批记录
#      （CI 上表现为 expected: SENT but was: SENDING）。cron 型不需要首次延迟，故不强制。
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
def anno(name):
    """注解匹配片段：容忍全限定写法（`@EnableCaching` 与
    `@org.springframework.cache.annotation.EnableCaching` 都必须被识别）。

    2026-09-11 负向实测发现：原先只匹配简单名，把注解写成全限定名即可静默绕过门禁——
    能一键绕过的门禁比没有门禁更危险，因为它提供虚假的信心。
    """
    return rf"@(?:[A-Za-z_][\w]*\.)*{name}\b"


def java_files(pattern):
    for path in sorted(root.rglob("*.java")):
        if "/target/" in path.as_posix():
            continue
        if re.search(pattern, path.read_text(encoding="utf-8", errors="ignore"), re.MULTILINE):
            yield path


# 自动装配登记集合：调度与缓存两处「生效性」检查共用。
imports_registrations = set()
for imports_file in root.rglob("org.springframework.boot.autoconfigure.AutoConfiguration.imports"):
    if "/target/" in imports_file.as_posix():
        continue
    imports_registrations.update(
        line.strip()
        for line in imports_file.read_text(encoding="utf-8", errors="ignore").splitlines()
        if line.strip() and not line.strip().startswith("#")
    )


def annotation_block(lines, start_lineno):
    """从 @Xxx( 起始行开始，按括号配平取出整段注解文本（支持单行与多行）。"""
    depth = 0
    started = False
    collected = []
    for line in lines[start_lineno - 1:]:
        collected.append(line)
        for char in line:
            if char == "(":
                depth += 1
                started = True
            elif char == ")":
                depth -= 1
        if started and depth <= 0:
            break
    return "\n".join(collected)


scheduled = []
for path in java_files(r"^\s*" + anno("Scheduled")):
    text = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    for lineno, line in enumerate(text, start=1):
        if re.match(r"\s*" + anno("Scheduled"), line):
            scheduled.append((path.relative_to(root).as_posix(), lineno))

if scheduled:
    enable_files = [
        path
        for path in java_files(r"^\s*" + anno("EnableScheduling"))
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
            for lineno, line_text in enumerate(lines, start=1):
                if not re.search(anno("ConditionalOnProperty"), line_text):
                    continue
                # 逐条注解判断：只有**这条**条件注解自己声明了 matchIfMissing=true 才算默认开启。
                # 早先的写法是"整个文件里出现过 matchIfMissing=true 就算过"，会被同文件里另一条
                # 无关的条件注解满足（2026-09-11 负向实测证明可绕过）。
                block = annotation_block(lines, lineno)
                if re.search(r"matchIfMissing\s*=\s*true", block):
                    continue
                violation(
                    f"{path.relative_to(root).as_posix()}:{lineno}",
                    "@EnableScheduling 被 @ConditionalOnProperty 门控且未声明 matchIfMissing=true："
                    "开关关闭时全局调度静默失效",
                )

# ---- fixedDelay/fixedRate 型 @Scheduled 必须声明首次执行延迟 ----------------
# 只检查 main 源码：测试里的 @Scheduled 探针（例如 AinerSchedulingAutoConfigurationTest）
# 正是要断言"调度生效即立即执行"，给它加首次延迟会让这条断言失去意义。
for path in java_files(r"^\s*" + anno("Scheduled")):
    if "/src/main/java/" not in path.as_posix():
        continue
    lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    for lineno, line in enumerate(lines, start=1):
        if not re.match(r"\s*@Scheduled\b", line):
            continue
        block = annotation_block(lines, lineno)
        if "cron" in block and "fixedDelay" not in block and "fixedRate" not in block:
            continue  # cron 型由表达式决定触发时刻，不需要首次延迟
        if "initialDelayString" in block or re.search(r"initialDelay\s*=", block):
            continue
        violation(
            f"{path.relative_to(root).as_posix()}:{lineno}",
            "@Scheduled 使用 fixedDelay/fixedRate 但未声明 initialDelay：Spring 会在上下文刷新后"
            "立即执行一次，启动尚未完成就抢跑并与其他消费者争抢（2026-09-11 投递引擎 CI 缺陷）",
        )


# ---- @Cacheable/@CacheEvict/@CachePut ⇒ 生效的 @EnableCaching --------------
cache_annotations = r"^\s*" + anno("(?:Cacheable|CacheEvict|CachePut)")
cache_users = [
    path for path in java_files(cache_annotations)
    if "/src/main/java/" in path.as_posix()
]
if cache_users:
    caching_enablers = [
        path for path in java_files(r"^\s*" + anno("EnableCaching"))
        if "/src/main/java/" in path.as_posix()
    ]
    if not caching_enablers:
        for path in cache_users:
            violation(
                f"{path.relative_to(root).as_posix()}",
                "@Cacheable/@CacheEvict/@CachePut 存在但 main 源码中没有 @EnableCaching："
                "缓存注解不会生效（Spring Boot 不会替你打开缓存）",
            )
    else:
        caching_autoconfigs = []
        for path in caching_enablers:
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
            caching_autoconfigs.append(
                (path.relative_to(root).as_posix(), class_line, f"{package}.{class_name}")
            )
        if caching_autoconfigs and not any(
            fqcn in imports_registrations for _, _, fqcn in caching_autoconfigs
        ):
            for path, lineno, fqcn in caching_autoconfigs:
                violation(
                    f"{path}:{lineno}",
                    f"{fqcn} 未登记进任何 AutoConfiguration.imports：类存在但启动时不注册，"
                    "缓存注解仍然不生效",
                )
        for path in caching_enablers:
            lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
            for lineno, line_text in enumerate(lines, start=1):
                if not re.search(anno("ConditionalOnProperty"), line_text):
                    continue
                block = annotation_block(lines, lineno)
                if re.search(r"matchIfMissing\s*=\s*true", block):
                    continue
                violation(
                    f"{path.relative_to(root).as_posix()}:{lineno}",
                    "@EnableCaching 被 @ConditionalOnProperty 门控且未声明 matchIfMissing=true："
                    "开关关闭时缓存注解静默失效",
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
    f"{len(scheduled)} 处 @Scheduled 均有生效的 @EnableScheduling 与首次执行延迟；"
    f"{len(cache_users)} 个 main 源码文件使用缓存注解且缓存切面已生效"
)
PY

exit "$status"
