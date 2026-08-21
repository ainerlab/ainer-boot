#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
artifact_manifest="$boot_root/scripts/release-artifacts.txt"
release_version="${AINER_VERSION:-}"
source_sha="${AINER_RELEASE_SOURCE_SHA:-${GITHUB_SHA:-}}"
release_tag="${AINER_RELEASE_TAG:-${GITHUB_REF_NAME:-}}"
repository_slug="${GITHUB_REPOSITORY:-ainerlab/ainer-boot}"
package_repository="${AINER_PACKAGE_REPOSITORY:-https://maven.pkg.github.com/ainerlab/ainer-boot}"
package_username="${AINER_PACKAGE_USERNAME:-${GITHUB_ACTOR:-}}"
package_token="${AINER_PACKAGE_TOKEN:-${GITHUB_TOKEN:-}}"
output_dir="${AINER_RELEASE_OUTPUT_DIR:-}"
sbom_path="${AINER_SBOM_PATH:-}"
gpg_fingerprint="${AINER_GPG_FINGERPRINT:-}"
gpg_passphrase="${MAVEN_GPG_PASSPHRASE:-}"

fail() {
  echo "[ainer-release-evidence] ERROR: $*" >&2
  exit 1
}

semver_pattern='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-((0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(\.(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'
[[ "$release_version" =~ $semver_pattern ]] \
  || fail "AINER_VERSION must be a semantic non-SNAPSHOT version"
[[ "$release_version" != *-SNAPSHOT ]] || fail "release version cannot contain SNAPSHOT"
[[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail "release source SHA must be a full Git commit SHA"
[[ "$release_tag" == "v$release_version" ]] || fail "tag/version mismatch: $release_tag vs $release_version"
[[ "$repository_slug" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || fail "invalid GITHUB_REPOSITORY"
[[ -n "$package_username" ]] || fail "package username is missing"
[[ -n "$package_token" ]] || fail "package token is missing"
[[ "$package_username" != *[$'\t\r\n ']* ]] || fail "package username contains unsupported whitespace"
[[ "$package_token" != *[$'\r\n']* ]] || fail "package token contains a newline"
[[ -n "$output_dir" ]] || fail "AINER_RELEASE_OUTPUT_DIR is required"
[[ -f "$sbom_path" ]] || fail "CycloneDX SBOM is missing: $sbom_path"
[[ -f "$artifact_manifest" ]] || fail "release artifact manifest is missing: $artifact_manifest"
[[ "$gpg_fingerprint" =~ ^[0-9A-Fa-f]{40}$ ]] || fail "AINER_GPG_FINGERPRINT must be a 40-character fingerprint"
[[ -n "$gpg_passphrase" ]] || fail "MAVEN_GPG_PASSPHRASE is required"
command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v gpg >/dev/null 2>&1 || fail "gpg is required"
command -v python3 >/dev/null 2>&1 || fail "python3 is required"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required"
command -v sha512sum >/dev/null 2>&1 || fail "sha512sum is required"

[[ "$package_repository" =~ ^https?://[A-Za-z0-9._-]+(:[0-9]+)?(/.*)?$ ]] \
  || fail "AINER_PACKAGE_REPOSITORY must be an absolute HTTP(S) URL"
package_repository="${package_repository%/}"
package_host="${package_repository#*://}"
package_host="${package_host%%/*}"

mkdir -p "$output_dir"
maven_dir="$output_dir/maven-artifacts"
[[ ! -e "$maven_dir" ]] || fail "refusing to overwrite existing artifact directory: $maven_dir"
mkdir -p "$maven_dir"

temporary_parent="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
netrc_file="$(mktemp "$temporary_parent/ainer-release-netrc.XXXXXX")"
cleanup() {
  rm -f -- "$netrc_file"
}
trap cleanup EXIT
chmod 600 "$netrc_file"
printf 'machine %s\nlogin %s\npassword %s\n' \
  "$package_host" "$package_username" "$package_token" >"$netrc_file"

download_and_verify() {
  local artifact="$1"
  local filename="$2"
  local artifact_dir="$maven_dir/dev/ainer/$artifact/$release_version"
  local artifact_url="$package_repository/dev/ainer/$artifact/$release_version/$filename"
  local file="$artifact_dir/$filename"
  local status_output

  mkdir -p "$artifact_dir"
  curl --fail --silent --show-error --location \
    --retry 12 --retry-delay 5 --retry-max-time 90 --retry-all-errors \
    --netrc-file "$netrc_file" \
    --output "$file" \
    "$artifact_url"
  curl --fail --silent --show-error --location \
    --retry 12 --retry-delay 5 --retry-max-time 90 --retry-all-errors \
    --netrc-file "$netrc_file" \
    --output "$file.asc" \
    "$artifact_url.asc"

  status_output="$(gpg --batch --status-fd 1 --verify "$file.asc" "$file" 2>/dev/null)" \
    || fail "GPG verification failed for $filename"
  if ! awk -v expected="${gpg_fingerprint^^}" '
      $1 == "[GNUPG:]" && $2 == "VALIDSIG" {
        if (toupper($3) == expected || toupper($NF) == expected) found = 1
      }
      END { exit found ? 0 : 1 }
    ' <<<"$status_output"; then
    fail "signature for $filename was not made by $gpg_fingerprint"
  fi
}

project_count=0
expected_primary_count=0
while read -r artifact packaging classifiers extra; do
  [[ -n "$artifact" ]] || continue
  [[ -z "${extra:-}" ]] || fail "invalid release artifact manifest row for $artifact"
  [[ "$artifact" =~ ^[a-z0-9-]+$ ]] || fail "invalid artifactId in release artifact manifest: $artifact"
  [[ "$packaging" == "pom" || "$packaging" == "jar" ]] \
    || fail "unsupported packaging in release artifact manifest: $packaging"

  project_count=$((project_count + 1))
  download_and_verify "$artifact" "$artifact-$release_version.pom"
  download_and_verify "$artifact" "$artifact-$release_version-build.pom"
  expected_primary_count=$((expected_primary_count + 2))

  if [[ "$packaging" == "jar" ]]; then
    download_and_verify "$artifact" "$artifact-$release_version.jar"
    download_and_verify "$artifact" "$artifact-$release_version-sources.jar"
    download_and_verify "$artifact" "$artifact-$release_version-javadoc.jar"
    expected_primary_count=$((expected_primary_count + 3))
  fi

  if [[ "$classifiers" != "-" ]]; then
    IFS=',' read -r -a classifier_list <<<"$classifiers"
    for classifier in "${classifier_list[@]}"; do
      [[ "$classifier" =~ ^[A-Za-z0-9_.-]+$ ]] \
        || fail "invalid classifier in release artifact manifest: $classifier"
      download_and_verify "$artifact" "$artifact-$release_version-$classifier.jar"
      expected_primary_count=$((expected_primary_count + 1))
    done
  fi
done <"$artifact_manifest"

[[ "$project_count" == "27" ]] \
  || fail "release artifact manifest must contain 27 projects, found $project_count"
[[ "$expected_primary_count" == "127" ]] \
  || fail "release artifact manifest must describe 127 primary artifacts, found $expected_primary_count"

primary_count="$(find "$maven_dir" -type f ! -name '*.asc' | wc -l | tr -d ' ')"
signature_count="$(find "$maven_dir" -type f -name '*.asc' | wc -l | tr -d ' ')"
[[ "$primary_count" == "$expected_primary_count" ]] \
  || fail "expected $expected_primary_count primary Maven artifacts, found $primary_count"
[[ "$signature_count" == "$expected_primary_count" ]] \
  || fail "expected $expected_primary_count Maven signatures, found $signature_count"

(
  cd "$maven_dir"
  find . -type f ! -name '*.asc' -print0 | sort -z | xargs -0 sha256sum
) >"$output_dir/MAVEN-SHA256SUMS"
(
  cd "$maven_dir"
  find . -type f ! -name '*.asc' -print0 | sort -z | xargs -0 sha512sum
) >"$output_dir/MAVEN-SHA512SUMS"

public_key_path="$output_dir/ainer-release-public-key.asc"
gpg --batch --armor --export "$gpg_fingerprint" >"$public_key_path"
[[ -s "$public_key_path" ]] || fail "failed to export the release public key"

fingerprint_path="$output_dir/ainer-release-key-fingerprint.txt"
{
  printf 'fingerprint=%s\n' "${gpg_fingerprint^^}"
  printf 'version=%s\n' "$release_version"
  printf 'tag=%s\n' "$release_tag"
  printf 'source_sha=%s\n' "$source_sha"
} >"$fingerprint_path"

provenance_path="$output_dir/ainer-boot-$release_version.provenance.json"
AINER_PROVENANCE_MANIFEST="$output_dir/MAVEN-SHA256SUMS" \
AINER_PROVENANCE_OUTPUT="$provenance_path" \
AINER_PROVENANCE_VERSION="$release_version" \
AINER_PROVENANCE_TAG="$release_tag" \
AINER_PROVENANCE_SHA="$source_sha" \
AINER_PROVENANCE_REPOSITORY="$repository_slug" \
python3 - <<'PY'
import json
import os
from pathlib import Path

subjects = []
for line in Path(os.environ["AINER_PROVENANCE_MANIFEST"]).read_text(encoding="utf-8").splitlines():
    digest, name = line.split(maxsplit=1)
    subjects.append({"name": name.removeprefix("./"), "digest": {"sha256": digest}})

repository = os.environ["AINER_PROVENANCE_REPOSITORY"]
source_sha = os.environ["AINER_PROVENANCE_SHA"]
run_id = os.environ.get("GITHUB_RUN_ID", "local")
run_attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "1")
workflow_ref = os.environ.get(
    "GITHUB_WORKFLOW_REF",
    f"{repository}/.github/workflows/release.yml@{os.environ['AINER_PROVENANCE_TAG']}",
)
statement = {
    "_type": "https://in-toto.io/Statement/v1",
    "subject": subjects,
    "predicateType": "https://github.com/ainerlab/ainer-boot/attestations/release-provenance/v1",
    "predicate": {
        "version": os.environ["AINER_PROVENANCE_VERSION"],
        "tag": os.environ["AINER_PROVENANCE_TAG"],
        "source": {
            "uri": f"git+https://github.com/{repository}.git",
            "digest": {"gitCommit": source_sha},
        },
        "builder": {"id": "https://github.com/actions/runner"},
        "invocation": {
            "workflowRef": workflow_ref,
            "runId": run_id,
            "runAttempt": run_attempt,
            "runUrl": f"https://github.com/{repository}/actions/runs/{run_id}",
        },
        "artifactPolicy": {
            "primaryArtifactCount": len(subjects),
            "signatureRequired": True,
            "signatureFormat": "OpenPGP detached ASCII armor",
        },
    },
}
Path(os.environ["AINER_PROVENANCE_OUTPUT"]).write_text(
    json.dumps(statement, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)
PY

release_notes_path="$output_dir/release-evidence.md"
cat >"$release_notes_path" <<EOF
## Ainer Boot $release_version release evidence

- Source: \`$source_sha\` (annotated tag \`$release_tag\`)
- Maven artifacts: 127 primary files, each read back from GitHub Packages with a valid detached OpenPGP signature
- Consumers: Maven 3.9+ and Maven 4 run from separate empty local repositories; Project Initializer is fetched remotely
- Evidence: CycloneDX SBOM, SHA-256/SHA-512 manifests, signed provenance statement, release public key and fingerprint
- Build: https://github.com/$repository_slug/actions/runs/${GITHUB_RUN_ID:-local}

The project-signed provenance statement records source and artifact digests. It is not a claim of a GitHub
Attestation or a certified SLSA build level. GitHub Attestations, when enabled for the repository, are an additional
blocking attestation rather than a substitute for this evidence.
EOF

sign_file() {
  local file="$1"
  printf '%s' "$gpg_passphrase" \
    | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
      --local-user "$gpg_fingerprint" --armor --detach-sign \
      --output "$file.asc" "$file"
  gpg --batch --verify "$file.asc" "$file" >/dev/null 2>&1 \
    || fail "failed to verify evidence signature for $file"
}

sign_file "$output_dir/MAVEN-SHA256SUMS"
sign_file "$output_dir/MAVEN-SHA512SUMS"
sign_file "$sbom_path"
sign_file "$provenance_path"
sign_file "$release_notes_path"
release_manifest_path="$output_dir/ainer-release-artifacts.txt"
cp -- "$artifact_manifest" "$release_manifest_path"
sign_file "$release_manifest_path"

evidence_manifest="$output_dir/EVIDENCE-SHA256SUMS"
(
  cd "$output_dir"
  find . -maxdepth 1 -type f \
    ! -name 'EVIDENCE-SHA256SUMS*' \
    -print0 | sort -z | xargs -0 sha256sum
) >"$evidence_manifest"
sign_file "$evidence_manifest"

echo "[ainer-release-evidence] verified $primary_count remote Maven artifacts and $signature_count signatures"
echo "[ainer-release-evidence] evidence written to $output_dir"
