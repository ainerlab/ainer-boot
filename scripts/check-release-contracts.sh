#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
release_workflow="$boot_root/.github/workflows/release.yml"
artifact_manifest="$boot_root/scripts/release-artifacts.txt"
initializer_templates="$boot_root/ainer-initializer/src/main/resources/templates/v1"

fail() {
  echo "[ainer-release-contracts] ERROR: $*" >&2
  exit 1
}

while IFS= read -r script; do
  bash -n "$script"
done < <(find "$boot_root/scripts" -maxdepth 1 -type f -name '*.sh' | sort)

[[ -f "$artifact_manifest" ]] || fail "release artifact manifest is missing"
temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/ainer-release-contracts.XXXXXX")"
cleanup() {
  case "$temporary_dir" in
    "${TMPDIR:-/tmp}"/ainer-release-contracts.*) rm -rf -- "$temporary_dir" ;;
    *) echo "[ainer-release-contracts] refusing unsafe cleanup target: $temporary_dir" >&2 ;;
  esac
}
trap cleanup EXIT

manifest_projects="$temporary_dir/manifest-projects.txt"
actual_projects="$temporary_dir/actual-projects.txt"
awk '
  NF != 3 { invalid = 1; next }
  $1 !~ /^[a-z0-9-]+$/ { invalid = 1; next }
  $2 != "pom" && $2 != "jar" { invalid = 1; next }
  $3 != "-" && $3 !~ /^[A-Za-z0-9_.-]+(,[A-Za-z0-9_.-]+)*$/ { invalid = 1; next }
  { print $1, $2 }
  END { exit invalid ? 1 : 0 }
' "$artifact_manifest" | sort >"$manifest_projects" \
  || fail "release artifact manifest contains an invalid row"
[[ "$(wc -l <"$manifest_projects" | tr -d ' ')" == "28" ]] \
  || fail "release artifact manifest must contain exactly 28 projects"
[[ -z "$(cut -d' ' -f1 "$manifest_projects" | uniq -d)" ]] \
  || fail "release artifact manifest contains duplicate artifactIds"

while IFS= read -r -d '' pom; do
  artifact="$(awk '
    /<parent>/ { in_parent = 1 }
    /<\/parent>/ { in_parent = 0; next }
    !in_parent && /<artifactId>/ {
      line = $0
      sub(/^.*<artifactId>/, "", line)
      sub(/<\/artifactId>.*$/, "", line)
      print line
      exit
    }
  ' "$pom")"
  [[ -n "$artifact" ]] || fail "cannot read project artifactId from $pom"
  packaging="$(sed -n 's:.*<packaging>\([^<]*\)</packaging>.*:\1:p' "$pom" | sed -n '1p')"
  printf '%s %s\n' "$artifact" "${packaging:-jar}"
done < <(
  find "$boot_root" -name pom.xml -type f \
    ! -path '*/target/*' \
    ! -path '*/ainer-initializer/src/main/resources/templates/*' \
    -print0
) | sort >"$actual_projects"

if ! diff -u "$manifest_projects" "$actual_projects"; then
  fail "release artifact manifest does not match the reactor POM inventory"
fi

if grep -n -E '/usr/sbin/ab|AINNER_VERSION' "$boot_root/scripts/measure-virtual-threads.sh"; then
  fail "virtual-thread tooling must resolve ab from PATH and must not misspell AINER_VERSION"
fi
# 正面断言：脚本必须真的从单 N 的 AINER_VERSION 解析版本。原守卫只反查双 N 拼写
# （AINNER_VERSION）——在拼写修正落地后该条件恒不成立，守卫沦为永不触发的空检查，
# 变量被改坏/改名都不会被发现（docs/project-status.md 记录了拼写修正但守卫未同步）。
grep -Fq 'AINER_VERSION' "$boot_root/scripts/measure-virtual-threads.sh" \
  || fail "virtual-thread tooling must resolve the Ainer version from AINER_VERSION"

for wrapper_asset in mvnw mvnw.cmd maven-wrapper.properties; do
  [[ -f "$initializer_templates/$wrapper_asset" ]] \
    || fail "initializer Maven Wrapper asset is missing: $wrapper_asset"
done
grep -Fq 'apache-maven/3.9.16/apache-maven-3.9.16-bin.zip' \
  "$initializer_templates/maven-wrapper.properties" \
  || fail "initializer wrapper must pin Maven 3.9.16"
grep -Fq 'distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce' \
  "$initializer_templates/maven-wrapper.properties" \
  || fail "initializer Maven distribution checksum is missing or changed"
grep -Fq 'plain_wrapper="$generated_dir/mvnw"' \
  "$boot_root/scripts/verify-initializer-consumer.sh" \
  || fail "initializer consumer gate must execute the generated Maven Wrapper"
grep -Fq 'consumer_wrapper="$generated_dir/mvnw"' \
  "$boot_root/scripts/measure-ttfr.sh" \
  || fail "TTFR gate must execute the generated Maven Wrapper"
grep -Fq 'consumer_wrapper="$generated_dir/mvnw"' \
  "$boot_root/scripts/measure-ttcrud.sh" \
  || fail "TTCRUD gate must execute the generated Maven Wrapper"
grep -Fq 'assert_ab_result' "$boot_root/scripts/measure-virtual-threads.sh" \
  || fail "virtual-thread matrix must fail closed on ApacheBench results"
if grep -n -- '-Dgpg.passphrase' "$release_workflow"; then
  fail "GPG passphrases must never be passed on the Maven command line"
fi
if grep -n -A2 'name: Attest build provenance' "$release_workflow" | grep -q 'continue-on-error'; then
  fail "enabled build attestation must fail closed"
fi

for pom in "$boot_root/pom.xml" "$boot_root/ainer-dependencies/pom.xml"; do
  grep -Fq '<bestPractices>true</bestPractices>' "$pom" \
    || fail "maven-gpg-plugin best-practices mode is missing from $pom"
  grep -Fq '<passphraseEnvName>MAVEN_GPG_PASSPHRASE</passphraseEnvName>' "$pom" \
    || fail "maven-gpg-plugin passphrase environment binding is missing from $pom"
done

required_release_markers=(
  'verify-release-ref.sh'
  'check-package-version-absent.sh'
  'AINER_IMMUTABLE_RELEASES'
  'AINER_RELEASE_GPG_FINGERPRINT'
  'AINER_ARTIFACT_SOURCE: remote'
  'verify-remote-release-artifacts.sh'
  'AINER_COMMERCIAL_VERSION'
  'Generate CycloneDX release SBOM'
  'Create immutable GitHub Release'
  'release_immutable'
)
for marker in "${required_release_markers[@]}"; do
  grep -Fq "$marker" "$release_workflow" \
    || fail "release workflow is missing required marker: $marker"
done

"$boot_root/scripts/check-commercial-docs.sh"

# 运行时装配门禁：Dockerfile COPY 覆盖 reactor 模块 + @Scheduled 有生效的 @EnableScheduling。
# 与 CI 的独立步骤同源，保证本地 `check-release-contracts.sh` 也能拦住同类回归。
"$boot_root/scripts/check-runtime-wiring.sh"

echo "[ainer-release-contracts] shell, runtime wiring, commercial documentation and release workflow contracts passed"
