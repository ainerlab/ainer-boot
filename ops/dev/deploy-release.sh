#!/usr/bin/env bash
set -euo pipefail

jar="${1:-}"
expected_commit="${2:-}"
release_keep="${3:-5}"
deploy_root="/opt/ainer-boot/authorization-server"
runtime_env="/etc/ainer/authorization-server-dev.env"
service="ainer-authorization-server-dev.service"

fail() {
  echo "[ainer-boot-deploy] ERROR: $*" >&2
  exit 1
}

[[ "$(id -u)" -eq 0 ]] || fail "deploy-release.sh must run as root"
[[ "$jar" =~ ^/[A-Za-z0-9._/-]+$ && "$jar" != *".."* && -f "$jar" ]] \
  || fail "artifact path is missing or unsafe"
[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] \
  || fail "expected commit must be a full lowercase Git SHA"
[[ "$release_keep" =~ ^[1-9][0-9]*$ ]] || fail "release keep count must be positive"
[[ -f "$runtime_env" ]] || fail "runtime environment is missing; run bootstrap-host.sh first"

exec 9>"$deploy_root/.deploy.lock"
flock -w 120 9 || fail "another Ainer Boot deployment holds the lock"

release_id="${expected_commit:0:12}-$(date -u +%Y%m%d%H%M%S)"
release_dir="$deploy_root/releases/$release_id"
staging_dir="$deploy_root/.staging-$release_id-$$"
link_tmp="$deploy_root/.current-$$"

cleanup() {
  rm -rf -- "$staging_dir"
  rm -f -- "$link_tmp" "$jar"
}
trap cleanup EXIT

[[ ! -e "$release_dir" ]] || fail "release already exists: $release_id"
install -d -o root -g ainer -m 0750 "$staging_dir"
install -o root -g ainer -m 0640 "$jar" "$staging_dir/ainer-authorization-server.jar"
artifact_sha256="$(sha256sum "$staging_dir/ainer-authorization-server.jar" | cut -d' ' -f1)"
cat >"$staging_dir/release.json" <<EOF
{
  "schemaVersion": 1,
  "product": "Ainer Authorization Server",
  "commit": "$expected_commit",
  "artifactSha256": "$artifact_sha256",
  "internalPort": 19000
}
EOF
chown root:ainer "$staging_dir/release.json"
chmod 0640 "$staging_dir/release.json"

mv "$staging_dir" "$release_dir"
ln -s "$release_dir" "$link_tmp"
mv -Tf "$link_tmp" "$deploy_root/current"

wait_for_ready() {
  local ready=false
  sleep 3
  for _ in {1..180}; do
    if systemctl is-failed --quiet "$service"; then
      journalctl -u "$service" -n 80 --no-pager >&2
      fail "Authorization Server entered failed state"
    fi
    if curl --noproxy '*' -fsS \
      http://127.0.0.1:19000/.well-known/openid-configuration \
      | grep -Fq '"issuer":"https://ainer-dev.xiaoqu99.com"'; then
      ready=true
      break
    fi
    sleep 1
  done
  [[ "$ready" == true ]] || fail "Authorization Server did not become ready"
}

systemctl reset-failed "$service" 2>/dev/null || true
systemctl restart "$service"
wait_for_ready

# Fixture passwords are needed only for the first idempotent initialization. Keep a
# root-only handoff file for dev acceptance, but remove them from the Java process.
if grep -Fxq 'AINER_ADMIN_DEV_BOOTSTRAP_ENABLED=true' "$runtime_env"; then
  sealed_env="$runtime_env.sealed.$$"
  sed \
    -e 's/^AINER_ADMIN_DEV_BOOTSTRAP_ENABLED=true$/AINER_ADMIN_DEV_BOOTSTRAP_ENABLED=false/' \
    -e '/^AINER_ADMIN_DEV_OWNER_PASSWORD=/d' \
    -e '/^AINER_ADMIN_DEV_MEMBER_PASSWORD=/d' \
    "$runtime_env" >"$sealed_env"
  chown root:ainer "$sealed_env"
  chmod 0640 "$sealed_env"
  mv -f "$sealed_env" "$runtime_env"
  systemctl restart "$service"
  wait_for_ready
fi

current_target="$(readlink -f "$deploy_root/current")"
mapfile -t releases < <(
  find "$deploy_root/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
    | sort -nr \
    | cut -d' ' -f2-
)
for ((index = release_keep; index < ${#releases[@]}; index += 1)); do
  [[ "${releases[$index]}" == "$current_target" ]] || rm -rf -- "${releases[$index]}"
done

echo "AINER_BOOT_RELEASE_ID=$release_id"
echo "AINER_BOOT_RELEASE_COMMIT=$expected_commit"
echo "AINER_BOOT_ARTIFACT_SHA256=$artifact_sha256"
