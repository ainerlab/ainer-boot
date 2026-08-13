#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
release_tag="${AINER_RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
release_sha="${AINER_RELEASE_SHA:-${GITHUB_SHA:-}}"
release_base_ref="${AINER_RELEASE_BASE_REF:-}"

fail() {
  echo "[ainer-release-ref] ERROR: $*" >&2
  exit 1
}

[[ -n "$release_tag" ]] || fail "release tag is missing (set AINER_RELEASE_TAG or GITHUB_REF_NAME)"
[[ -n "$release_sha" ]] || fail "release SHA is missing (set AINER_RELEASE_SHA or GITHUB_SHA)"

release_version="${release_tag#v}"
semver_pattern='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-((0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(\.(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'
if [[ "$release_tag" != v* \
    || ! "$release_version" =~ $semver_pattern ]]; then
  fail "release tag is not a supported semantic version: $release_tag"
fi
[[ "$release_version" != *-SNAPSHOT ]] || fail "release tag cannot contain SNAPSHOT: $release_tag"

cd "$boot_root"
tag_type="$(git cat-file -t "refs/tags/$release_tag" 2>/dev/null || true)"
[[ "$tag_type" == "tag" ]] \
  || fail "release refs must be annotated tags (got ${tag_type:-missing} for $release_tag)"

tag_commit="$(git rev-parse --verify "refs/tags/$release_tag^{commit}" 2>/dev/null || true)"
event_commit="$(git rev-parse --verify "$release_sha^{commit}" 2>/dev/null || true)"
[[ -n "$tag_commit" ]] || fail "cannot peel release tag to a commit: $release_tag"
[[ -n "$event_commit" ]] || fail "cannot resolve release event SHA: $release_sha"
[[ "$tag_commit" == "$event_commit" ]] \
  || fail "tag/source mismatch: $release_tag -> $tag_commit, event -> $event_commit"

if [[ -n "$release_base_ref" ]]; then
  base_commit="$(git rev-parse --verify "$release_base_ref^{commit}" 2>/dev/null || true)"
  [[ -n "$base_commit" ]] || fail "cannot resolve release base ref: $release_base_ref"
  [[ "$tag_commit" == "$base_commit" ]] \
    || fail "release tag must point to the current default-branch head: $tag_commit != $base_commit"
fi

printf 'release_version=%s\n' "$release_version"
printf 'release_source_sha=%s\n' "$tag_commit"
echo "[ainer-release-ref] annotated tag $release_tag resolves to $tag_commit" >&2
