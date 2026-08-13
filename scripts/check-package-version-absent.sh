#!/usr/bin/env bash
set -euo pipefail

release_version="${AINER_VERSION:-}"
package_repository="${AINER_PACKAGE_REPOSITORY:-https://maven.pkg.github.com/ainerlab/ainer-boot}"
package_username="${AINER_PACKAGE_USERNAME:-${GITHUB_ACTOR:-}}"
package_token="${AINER_PACKAGE_TOKEN:-${GITHUB_TOKEN:-}}"

fail() {
  echo "[ainer-package-preflight] ERROR: $*" >&2
  exit 1
}

semver_pattern='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-((0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(\.(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'
[[ "$release_version" =~ $semver_pattern ]] \
  || fail "AINER_VERSION must be a semantic non-SNAPSHOT version"
[[ "$release_version" != *-SNAPSHOT ]] || fail "release version cannot contain SNAPSHOT"
[[ -n "$package_username" ]] || fail "package username is missing"
[[ -n "$package_token" ]] || fail "package token is missing"
[[ "$package_username" != *[$'\t\r\n ']* ]] || fail "package username contains unsupported whitespace"
[[ "$package_token" != *[$'\r\n']* ]] || fail "package token contains a newline"

[[ "$package_repository" =~ ^https?://[A-Za-z0-9._-]+(:[0-9]+)?(/.*)?$ ]] \
  || fail "AINER_PACKAGE_REPOSITORY must be an absolute HTTP(S) URL"
package_repository="${package_repository%/}"
package_host="${package_repository#*://}"
package_host="${package_host%%/*}"

temporary_parent="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
netrc_file="$(mktemp "$temporary_parent/ainer-package-netrc.XXXXXX")"
cleanup() {
  rm -f -- "$netrc_file"
}
trap cleanup EXIT
chmod 600 "$netrc_file"
printf 'machine %s\nlogin %s\npassword %s\n' \
  "$package_host" "$package_username" "$package_token" >"$netrc_file"

probe_url="$package_repository/dev/ainer/ainer-dependencies/$release_version/ainer-dependencies-$release_version.pom"
status="$({
  curl --silent --show-error --location --retry 2 \
    --netrc-file "$netrc_file" \
    --output /dev/null \
    --write-out '%{http_code}' \
    "$probe_url"
} || true)"

case "$status" in
  404)
    echo "[ainer-package-preflight] version $release_version is absent and may be published"
    ;;
  200)
    fail "version $release_version already exists; published versions are immutable and must not be overwritten"
    ;;
  401|403)
    fail "package registry authentication failed while checking version $release_version (HTTP $status)"
    ;;
  *)
    fail "unexpected package registry response while checking version $release_version (HTTP ${status:-curl-error})"
    ;;
esac
