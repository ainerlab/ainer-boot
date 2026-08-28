#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
commercial_dir="$boot_root/docs/commercial"
changelog="$boot_root/CHANGELOG.md"

fail() {
  echo "[ainer-commercial-docs] ERROR: $*" >&2
  exit 1
}

expected_version="${AINER_COMMERCIAL_VERSION:-}"
if [[ -z "$expected_version" ]]; then
  expected_version="$({
    sed -n 's/^## \[\([0-9][^]]*\)\].*/\1/p' "$changelog" || true
  } | sed -n '1p')"
fi

semver_pattern='^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$'
[[ "$expected_version" =~ $semver_pattern ]] \
  || fail "cannot resolve a SemVer commercial baseline from AINER_COMMERCIAL_VERSION or CHANGELOG.md"

expected_marker="商业事实基线：\`v$expected_version\`"
commercial_files=()
while IFS= read -r -d '' commercial_file; do
  commercial_files+=("$commercial_file")
done < <(find "$commercial_dir" -maxdepth 1 -type f -name '*.md' -print0 | sort -z)

[[ "${#commercial_files[@]}" == "6" ]] \
  || fail "commercial documentation suite must contain exactly 6 Markdown files"

for commercial_file in "${commercial_files[@]}"; do
  grep -Fq "$expected_marker" "$commercial_file" \
    || fail "$(basename "$commercial_file") is not aligned to $expected_marker"
done

if grep -R -n -F '草案 v0.1' "$commercial_dir"; then
  fail "obsolete suite-level draft version remains in commercial documentation"
fi

assert_contains() {
  local relative_file="$1"
  local expected_text="$2"
  grep -Fq "$expected_text" "$commercial_dir/$relative_file" \
    || fail "$relative_file is missing current release claim: $expected_text"
}

assert_contains 'README.md' "本套材料对应工程版本：\`v$expected_version\`"
assert_contains 'product-whitepaper.md' "本文对应工程版本：\`v$expected_version\`"
assert_contains 'customer-delivery-guide.md' "对应版本：\`v$expected_version\`"
assert_contains 'edition-tiers.md' "当前事实对照（截至 \`v$expected_version\`）"
assert_contains 'sales-one-pager.md' "本文对应工程版本：\`v$expected_version\`"
assert_contains 'gap-analysis-and-next-steps.md' '是否已是开箱即用的生产平台'

echo "[ainer-commercial-docs] commercial facts align to v$expected_version"
