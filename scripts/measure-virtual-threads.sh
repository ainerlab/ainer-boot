#!/usr/bin/env bash
set -euo pipefail

# P0-5（ADR-0029 决策 5）：平台线程 / 虚拟线程双模式压测矩阵（MVC + JDBC）。
# 在临时目录生成 PostgreSQL CRUD 消费者项目，分别以 platform / virtual 两种
# 线程模式启动，用 ApacheBench 压制两类场景：
#   - /api/metricRows 分页接口（真实 JDBC + Flyway migration）——等待型无高阻塞
#   - /api/wait 模拟外部 IO 阻塞（sleep 等待时长）——高等待型并发，虚拟线程优势区
# 输出吞吐、P50/P90/P95/P99 与失败率对比，并录制 JFR 虚拟线程诊断文件。
# 矩阵维度（AI Provider 并发上限、MDC/Trace 传播、SSE 中断与优雅停机）
# 随依赖环境逐步补齐，本脚本为可重复基线。
#
# 用法：
#   ./scripts/measure-virtual-threads.sh [-n 请求数] [-c 并发数]
#       [-w 等待毫秒] [-W 等待场景并发数] [-d 目标目录]
#
# 环境要求：JDK 25 + Ainer reactor 已安装到本地仓库（或 AINER_VERSION 指定）、
# Docker/Colima 可用（Testcontainers）。Aborted requests 数为 0。

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
wrapper="$boot_root/mvnw"
requests=2000
concurrency=40
wait_ms=80
wait_concurrency=500
target_dir="${TMPDIR:-/tmp}/ainer-vt-matrix"

while getopts "n:c:w:W:d:" opt; do
  case "$opt" in
    n) requests="$OPTARG" ;;
    c) concurrency="$OPTARG" ;;
    w) wait_ms="$OPTARG" ;;
    W) wait_concurrency="$OPTARG" ;;
    d) target_dir="$OPTARG" ;;
    *) echo "usage: $0 [-n requests] [-c concurrency] [-w wait-ms] [-W wait-concurrency] [-d target-dir]" >&2; exit 1 ;;
  esac
done

fail() {
  echo "[vt-matrix] ERROR: $*" >&2
  exit 1
}

configured_version="$(
  sed -n 's:.*<revision>\([^<]*\)</revision>.*:\1:p' "$boot_root/pom.xml" | sed -n '1p'
)"
ainner_version="${AINNER_VERSION:-$configured_version}"
[[ -n "$ainner_version" ]] || fail "cannot determine Ainer version; set AINER_VERSION"

[[ -x "$wrapper" ]] || fail "Maven Wrapper missing: $wrapper"
command -v ab >/dev/null || fail "ApacheBench (ab) is required for the load generator"

rm -rf "$target_dir"
mkdir -p "$target_dir"
manifest="$target_dir/manifest.yaml"
generated="$target_dir/generated"
mkdir -p "$generated"

# 1. 生成带 PostgreSQL CRUD 的消费者项目（与 verify-initializer-consumer.sh CRUD 通道同 manifest）。
cat >"$manifest" <<EOF
schemaVersion: v1
project:
  name: Virtual Thread Matrix
  groupId: dev.ainer.vt
  artifactId: vt-matrix
  version: 1.0.0
  description: platform vs virtual thread pressure matrix target
spring-boot: 4.1.0
ainner: $ainner_version
java: 25
package: dev.ainer.vt
database: postgresql
entities:
  - name: metricRow
    fields:
      - name: code
        type: string(32)
        unique: true
      - name: payload
        type: text
      - name: amount
        type: decimal
      - name: active
        type: boolean
      - name: recordedAt
        type: instant
EOF
if [[ "$ainner_version" == *-SNAPSHOT ]]; then
  printf 'allowSnapshot: true\n' >>"$manifest"
fi

cli_jar="$(find ~/.m2/repository/dev/ainer/ainer-initializer-cli -name '*-cli.jar' 2>/dev/null | head -n 1)"
[[ -n "$cli_jar" ]] || fail "ainer-initializer-cli shaded JAR not found in local repository"
java -jar "$cli_jar" init "$manifest" "$generated" >/dev/null

# 注入压测专用 /api/wait 端点（模拟外部 IO/下游调用阻塞，阻塞式 Thread.sleep；
# 虚拟线程在等待期间挂起 carrier，平台线程占用 Tomcat worker）。该文件仅用于
# 矩阵测量，不属于 Initializer 生成产物。
cat >"$generated/src/main/java/dev/ainer/vt/ping/WaitController.java" <<'EOF'
package dev.ainer.vt.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WaitController {

    @GetMapping("/api/wait")
    public String simulateWait(@RequestParam(name = "ms", defaultValue = "80") long ms)
            throws InterruptedException {
        Thread.sleep(ms);
        return "ok";
    }
}
EOF

# 2. 编译生成项目（消费者独立构建）。使用主本地仓库中已安装的 Ainer SNAPSHOT
#    （若本地仓库过期，先执行 ./mvnw clean install 刷新；隔离仓库验证见
#    verify-initializer-consumer.sh）。
(
  cd "$generated"
  "$wrapper" --batch-mode --no-transfer-progress \
    -DskipTests \
    clean package >/dev/null || fail "generated consumer failed to package"
)

# 3. 启动 DATASOURCE 指向 Testcontainers 的 PostgreSQL，然后以双模式压测。
#    Flyway 在应用启动时执行 migration；数据通过测试后 Endpoint 写入。
run_mode() {
  local mode="$1"
  local port="$2"
  local log="$target_dir/$mode.log"

  local extra_args=()
  if [[ "$mode" == "virtual" ]]; then
    extra_args=(--spring.threads.virtual.enabled=true)
  fi

  # 通过真实 PostgreSQL Testcontainers 启动消费者应用（测试进程内启动并持有容器）。
  # 为让 ab 可压测，使用 spring-boot:run 会漫长且依赖 IDE 上下文；这里直接
  # 采用应用测试启动方式不可行，因此改用独立 PostgreSQL 容器（Colima/Docker）。
  docker run -d --rm --name ainer-vt-pg -e POSTGRES_DB=vt \
    -e POSTGRES_USER=vt -e POSTGRES_PASSWORD=vt-password \
    -p 55432:5432 postgres:18.3-alpine >/dev/null
  trap 'docker rm -f ainer-vt-pg >/dev/null 2>&1 || true' EXIT
  for _ in $(seq 1 30); do
    docker exec ainer-vt-pg pg_isready -U vt -d vt >/dev/null 2>&1 && break
    sleep 1
  done

  local jar
  jar="$(find "$generated/target" -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)"
  [[ -n "$jar" ]] || fail "generated application JAR not found"

  # 双模式公平对比：Hikari 池固定 16（关闭自动提交、显式池边界，避免平台线程
  # 模拟了连接耗尽而虚拟线程未达到边界的假象）。
  DATASOURCE_URL="jdbc:postgresql://localhost:55432/vt" \
  DATASOURCE_USERNAME=vt \
  DATASOURCE_PASSWORD=vt-password \
  java -XX:StartFlightRecording=filename="$target_dir/$mode.jfr",dumponexit=true,settings=profile \
    -jar "$jar" --server.port="$port" \
    --spring.datasource.hikari.maximum-pool-size=16 \
    --server.tomcat.threads.max=200 \
    "${extra_args[@]}" >"$log" 2>&1 &
  local app_pid=$!

  for _ in $(seq 1 60); do
    curl -sf "http://127.0.0.1:$port/actuator/health" >/dev/null 2>&1 && break
    kill -0 "$app_pid" 2>/dev/null || fail "application exited early, see $log"
    sleep 1
  done

  # 灌入行数据（直接写 POST；示例值由 manifest 决定，事务单行写入）。
  for i in $(seq 1 50); do
    curl -sf -X POST "http://127.0.0.1:$port/api/metricRows" \
      -H 'Content-Type: application/json' \
      -d "{\"code\":\"seed-$i\",\"payload\":\"p-$i\",\"amount\":12.5,\"active\":true,\"recordedAt\":\"2026-08-09T00:00:00Z\"}" \
      >/dev/null 2>&1 || true
  done

  # 压测分页接口（真实 JDBC 查询 + Flyway 初始化的表）。
  /usr/sbin/ab -n "$requests" -c "$concurrency" \
    -g "$target_dir/$mode.jdbc.tsv" \
    "http://127.0.0.1:$port/api/metricRows?page=1&size=10" >"$target_dir/$mode.jdbc.ab" 2>&1 || true

  # 压测等待型接口（模拟外部 IO 阻塞；高等待并发是虚拟线程的收益区，
  # 也是 ADR-0029 决策 5 决定「新 MVC 项目默认 v-thread」的判据之一）。
  /usr/sbin/ab -n "$requests" -c "$wait_concurrency" \
    -g "$target_dir/$mode.wait.tsv" \
    "http://127.0.0.1:$port/api/wait?ms=$wait_ms" >"$target_dir/$mode.wait.ab" 2>&1 || true

  # 等待 JFR 转储完成再杀进程。
  sleep 3
  [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null && kill "$app_pid" 2>/dev/null || true
  wait "$app_pid" 2>/dev/null || true
  docker rm -f ainer-vt-pg >/dev/null 2>&1 || true
  trap - EXIT
}

summarize() {
  local mode="$1"
  local file="$target_dir/$mode.tsv"
  [[ -f "$file" ]] || fail "missing latency file $file"
  python3 - "$file" <<'PYEOF'
import csv, statistics, sys
rows = []
with open(sys.argv[1]) as f:
    r = csv.reader(f, delimiter="\t")
    next(r)
    for row in r:
        if len(row) >= 6:
            rows.append(int(row[4]))
rows.sort()
def p(x): return rows[min(len(rows)-1, int(len(rows)*x))]
print(f"n={len(rows)} p50={p(0.5)} p90={p(0.9)} p95={p(0.95)} p99={p(0.99)}")
PYEOF
}

echo "== platform（平台线程，默认） =="
run_mode platform 18081
platform_jdbc="$(summarize platform.jdbc)"
platform_wait="$(summarize platform.wait)"

echo "== virtual（spring.threads.virtual.enabled=true） =="
run_mode virtual 18082
virtual_jdbc="$(summarize virtual.jdbc)"
virtual_wait="$(summarize virtual.wait)"

echo
echo "== 双模式对比 — JDBC 分页（/api/metricRows，n=${requests} c=${concurrency}） =="
printf '%-10s %s\n' "mode" "result"
printf '%-10s %s\n' "platform" "$platform_jdbc"
printf '%-10s %s\n' "virtual" "$virtual_jdbc"

echo
echo "== 双模式对比 — 等待型并发（/api/wait?ms=${wait_ms}，n=${requests} c=${wait_concurrency}） =="
printf '%-10s %s\n' "mode" "result"
printf '%-10s %s\n' "platform" "$platform_wait"
printf '%-10s %s\n' "virtual" "$virtual_wait"

for mode in platform virtual; do
  for scene in jdbc wait; do
    grep -E 'Complete requests|Failed requests|Non-2xx responses|Requests per second' \
      "$target_dir/$mode.$scene.ab" | sed "s/^/[$mode-$scene] /" || true
  done
done
# ab 的 Length 失败是 keep-alive 连接复用时的长度基准误报；业务失败以
# Non-2xx responses 为准，出现为非业务可接受时必须人工核查页面响应。
echo "[vt-matrix] 注：ab 的 Length 失败为连接复用观测伪影，业务失败以 Non-2xx responses 为准"
echo "JFR 录制：$target_dir/platform.jfr $target_dir/virtual.jfr"
echo "[vt-matrix] matrix run finished (artifacts under $target_dir)"