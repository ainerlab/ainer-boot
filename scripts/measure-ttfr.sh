#!/usr/bin/env bash
set -euo pipefail

# Measures and gates TTFR (Time to First Run, docs/design/ainer-scaffold-design.md §12.1):
# from an empty directory to /actuator/health=UP in <= 600 seconds on the official
# reference environment with the artifact repository reachable.
#
# Optional overrides:
#   AINER_VERSION   Ainer version to install and generate against (default: pom <revision>)
#   TTFR_LIMIT_SEC  gate threshold in seconds (default: 600; TTFR_LIMIT remains compatible)
#   TTFR_START_REPO reuse an existing local repository (skip reactor install)

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
producer_wrapper="$boot_root/mvnw"

fail() {
  echo "[ainer-ttfr] ERROR: $*" >&2
  exit 1
}

configured_version="$(
  sed -n 's:.*<revision>\([^<]*\)</revision>.*:\1:p' "$boot_root/pom.xml" \
    | sed -n '1p'
)"
ainner_version="${AINER_VERSION:-$configured_version}"
ttfr_limit="${TTFR_LIMIT_SEC:-${TTFR_LIMIT:-600}}"
[[ -n "$ainner_version" ]] || fail "cannot determine the Ainer version; set AINER_VERSION explicitly"
[[ -x "$producer_wrapper" ]] \
  || fail "producer Maven Wrapper is missing or not executable: $producer_wrapper"

temporary_parent="${TMPDIR:-/tmp}"
temporary_dir="$(mktemp -d "$temporary_parent/ainer-ttfr.XXXXXX")"
local_repository="${TTFR_START_REPO:-$temporary_dir/repository}"
generated_dir="$temporary_dir/generated"
manifest="$temporary_dir/manifest.yaml"
cli_jar="$temporary_dir/initializer-cli.jar"

cleanup() {
  case "$temporary_dir" in
    "$temporary_parent"/ainer-ttfr.*)
      rm -rf -- "$temporary_dir"
      ;;
    *)
      echo "[ainer-ttfr] refusing unsafe cleanup target: $temporary_dir" >&2
      ;;
  esac
}
trap cleanup EXIT

cd "$boot_root"

# 1. Fresh empty target directory.
mkdir -p "$generated_dir"
[[ -z "$(ls -A "$generated_dir")" ]] || fail "target directory must be empty"

start_marker="$(date +%s)"

# 2. Install the reactor artifacts into the isolated repository (skip when reused).
if [[ -z "${TTFR_START_REPO:-}" ]]; then
  "$producer_wrapper" --batch-mode --no-transfer-progress \
    -Dmaven.repo.local="$local_repository" \
    -Drevision="$ainner_version" \
    -Dgpg.skip=true \
    -DskipTests \
    clean install >/dev/null 2>&1 || fail "reactor install failed"
fi

cat >"$manifest" <<EOF
schemaVersion: v1
project:
  name: TTFR Gate
  groupId: dev.ainer.consumer
  artifactId: ttfr-gate
  version: 1.0.0
  description: measured by the Ainer TTFT gate
spring-boot: 4.1.0
ainner: $ainner_version
java: 25
package: dev.ainer.consumer.gate
EOF
if [[ "$ainner_version" == *-SNAPSHOT ]]; then
  printf 'allowSnapshot: true\n' >>"$manifest"
fi

cli_jar="$(find "$local_repository/dev/ainer/ainer-initializer-cli" -name '*-cli.jar' | head -n 1)"
[[ -n "$cli_jar" ]] || fail "ainner-initializer-cli shaded JAR was not installed"
java -jar "$cli_jar" init "$manifest" "$generated_dir" >/dev/null \
  || fail "initializer failed to generate the consumer project"
consumer_wrapper="$generated_dir/mvnw"
[[ -x "$consumer_wrapper" ]] \
  || fail "generated Maven Wrapper is missing or not executable: $consumer_wrapper"

# 3. Launch via spring-boot:run and poll /actuator/health until UP.
cd "$generated_dir"
"$consumer_wrapper" --batch-mode --no-transfer-progress \
  -Dmaven.repo.local="$local_repository" \
  -DskipTests \
  spring-boot:run \
  >"$temporary_dir/run.log" 2>&1 &
app_pid=$!

poll_url="http://localhost:8080/actuator/health"
healthy=0
for attempt in $(seq 1 120); do
  if ! kill -0 "$app_pid" 2>/dev/null; then
    echo "[ainer-ttfr] application exited early; last log:" >&2
    tail -20 "$temporary_dir/run.log" >&2
    fail "consumer application terminated before becoming healthy"
  fi
  if curl --fail --silent --max-time 2 "$poll_url" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 2
done

kill "$app_pid" 2>/dev/null || true
wait "$app_pid" 2>/dev/null || true

if [[ "$healthy" != "1" ]]; then
  tail -20 "$temporary_dir/run.log" >&2
  fail "/actuator/health did not report UP within the probe window"
fi

elapsed="$(( $(date +%s) - start_marker ))"
echo "[ainer-ttfr] TTFR measured: ${elapsed}s (limit ${ttfr_limit}s)"
if [[ "$elapsed" -gt "$ttfr_limit" ]]; then
  fail "TTFR of ${elapsed}s exceeds the ${ttfr_limit}s gate"
fi
echo "[ainer-ttfr] TTFR gate passed"
