#!/usr/bin/env bash
set -euo pipefail

# Measures and gates TTCRUD (Time To CRUD, docs/design/ainer-scaffold-design.md §12.1 and
# ADR-0036): from a manifest with `entities` to a runnable PostgreSQL vertical CRUD with
# migration, API and a passing Testcontainers integration test in <= 1800 seconds on the
# official reference environment with the artifact repository reachable.
#
# Optional overrides:
#   AINER_VERSION    Ainer version to install and generate against (default: pom <revision>)
#   TTCRUD_LIMIT_SEC gate threshold in seconds (default: 1800)
#   TTCRUD_START_REPO reuse an existing local repository (skip reactor install)

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
wrapper="$boot_root/mvnw"

fail() {
  echo "[ainer-ttcrud] ERROR: $*" >&2
  exit 1
}

configured_version="$(
  sed -n 's:.*<revision>\([^<]*\)</revision>.*:\1:p' "$boot_root/pom.xml" \
    | sed -n '1p'
)"
ainner_version="${AINER_VERSION:-$configured_version}"
ttcrud_limit="${TTCRUD_LIMIT:-1800}"
[[ -n "$ainner_version" ]] || fail "cannot determine the Ainer version; set AINER_VERSION explicitly"
[[ -x "$wrapper" ]] || fail "Maven Wrapper is missing or not executable: $wrapper"

temporary_parent="${TMPDIR:-/tmp}"
temporary_dir="$(mktemp -d "$temporary_parent/ainer-ttcrud.XXXXXX")"
local_repository="$temporary_dir/repository"
generated_dir="$temporary_dir/generated"
manifest="$temporary_dir/manifest.yaml"
cli_jar="$temporary_dir/initializer-cli.jar"

cleanup() {
  case "$temporary_dir" in
    "$temporary_parent"/ainer-ttcrud.*)
      rm -rf -- "$temporary_dir"
      ;;
    *)
      echo "[ainer-ttcrud] refusing unsafe cleanup target: $temporary_dir" >&2
      ;;
  esac
}
trap cleanup EXIT

cd "$boot_root"

mkdir -p "$generated_dir"
[[ -z "$(ls -A "$generated_dir")" ]] || fail "target directory must be empty"

start_marker="$(date +%s)"

# 1. Install the reactor artifacts into the isolated repository (skip when reused).
if [[ -z "${TTCRUD_START_REPO:-}" ]]; then
  "$wrapper" --batch-mode --no-transfer-progress \
    -Dmaven.repo.local="$local_repository" \
    -Drevision="$ainner_version" \
    -Dgpg.skip=true \
    -DskipTests \
    clean install >/dev/null 2>&1 || fail "reactor install failed"
fi

cat >"$manifest" <<EOF
schemaVersion: v1
project:
  name: TTCRUD Gate
  groupId: dev.ainer.consumer
  artifactId: ttcrud-gate
  version: 1.0.0
  description: measured by the Ainer TTCRUD gate
spring-boot: 4.1.0
ainner: $ainner_version
java: 25
package: dev.ainer.consumer.gate
database: postgresql
entities:
  - name: product
    fields:
      - name: name
        type: string(64)
        comment: 产品名称
      - name: sku
        type: string(32)
        unique: true
        comment: 库存单位编码
      - name: price
        type: decimal
        nullable: true
      - name: active
        type: boolean
      - name: publishedAt
        type: instant
      - name: refId
        type: uuid
EOF
if [[ "$ainner_version" == *-SNAPSHOT ]]; then
  printf 'allowSnapshot: true\n' >>"$manifest"
fi

cli_jar="$(find "$local_repository/dev/ainer/ainer-initializer-cli" -name '*-cli.jar' | head -n 1)"
[[ -n "$cli_jar" ]] || fail "ainner-initializer-cli shaded JAR was not installed"
java -jar "$cli_jar" init "$manifest" "$generated_dir" >/dev/null \
  || fail "initializer failed to generate the CRUD consumer project"

# 2. The generated project must compile and run the PostgreSQL vertical CRUD tests green.
cd "$generated_dir"
"$wrapper" --batch-mode --no-transfer-progress \
  -Dmaven.repo.local="$local_repository" \
  -DskipTests \
  clean compile >/dev/null 2>&1 \
  || fail "generated CRUD consumer project failed to compile"

"$wrapper" --batch-mode --no-transfer-progress \
  -Dmaven.repo.local="$local_repository" \
  test \
  || fail "generated CRUD consumer integration tests failed"

report="$generated_dir/target/surefire-reports"
skipped=$(find "$report" -name '*.txt' -exec grep -h 'Tests run:' {} \; \
  | awk -F, '{for (i=1; i<=NF; i++) if ($i ~ /Skipped/) {gsub(/[^0-9]/, "", $i); s += $i}} END {print s+0}')
[[ "$skipped" == "0" ]] || fail "CRUD integration tests must run with 0 skipped (got $skipped)"
grep -q "CrudIntegrationTest" $(find "$report" -name '*.txt') \
  || fail "CRUD lifecycle integration test did not run"

elapsed="$(( $(date +%s) - start_marker ))"
echo "[ainer-ttcrud] TTCRUD measured: ${elapsed}s (limit ${ttcrud_limit}s)"
if [[ "$elapsed" -gt "$ttcrud_limit" ]]; then
  fail "TTCRUD of ${elapsed}s exceeds the ${ttcrud_limit}s gate"
fi
echo "[ainer-ttcrud] TTCRUD gate passed"
