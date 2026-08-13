#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
release_workflow="$boot_root/.github/workflows/release.yml"
artifact_manifest="$boot_root/scripts/release-artifacts.txt"

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
[[ "$(wc -l <"$manifest_projects" | tr -d ' ')" == "23" ]] \
  || fail "release artifact manifest must contain exactly 23 projects"
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
  fail "virtual-thread tooling must resolve ab from PATH and use AINER_VERSION"
fi
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
  'Generate CycloneDX release SBOM'
  'Create immutable GitHub Release'
  'release_immutable'
)
for marker in "${required_release_markers[@]}"; do
  grep -Fq "$marker" "$release_workflow" \
    || fail "release workflow is missing required marker: $marker"
done

echo "[ainer-release-contracts] shell and release workflow contracts passed"
