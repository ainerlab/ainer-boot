#!/usr/bin/env bash
set -euo pipefail

release_id="${1:-}"
deploy_root="/opt/ainer-boot/authorization-server"
service="ainer-authorization-server-dev.service"

fail() {
  echo "[ainer-boot-rollback] ERROR: $*" >&2
  exit 1
}

[[ "$(id -u)" -eq 0 ]] || fail "rollback-release.sh must run as root"
[[ "$release_id" =~ ^[0-9a-f]{12}-[0-9]{14}$ ]] || fail "invalid release id"
release_dir="$deploy_root/releases/$release_id"
[[ -f "$release_dir/ainer-authorization-server.jar" && -f "$release_dir/release.json" ]] \
  || fail "release is incomplete: $release_id"
expected_sha="$(sed -n 's/.*"artifactSha256": "\([0-9a-f]\{64\}\)".*/\1/p' "$release_dir/release.json")"
actual_sha="$(sha256sum "$release_dir/ainer-authorization-server.jar" | cut -d' ' -f1)"
[[ -n "$expected_sha" && "$actual_sha" == "$expected_sha" ]] \
  || fail "release checksum does not match"

exec 9>"$deploy_root/.deploy.lock"
flock -w 120 9 || fail "another Ainer Boot deployment holds the lock"
link_tmp="$deploy_root/.current-$$"
trap 'rm -f -- "$link_tmp"' EXIT
ln -s "$release_dir" "$link_tmp"
mv -Tf "$link_tmp" "$deploy_root/current"
systemctl restart "$service"

ready=false
for _ in {1..180}; do
  if curl --noproxy '*' -fsS \
    http://127.0.0.1:19000/.well-known/openid-configuration \
    | grep -Fq '"issuer":"https://ainer-dev.xiaoqu99.com"'; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || fail "rolled-back service did not become ready"
echo "AINER_BOOT_ROLLED_BACK_RELEASE=$release_id"
