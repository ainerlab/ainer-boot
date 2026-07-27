#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
deploy_host="${AINER_BOOT_DEPLOY_HOST:-ubuntu@dev.xiaoqu99.com}"

fail() {
  echo "[ainer-origin-bootstrap] ERROR: $*" >&2
  exit 1
}

[[ "$deploy_host" =~ ^[A-Za-z0-9._-]+@[A-Za-z0-9._-]+$ ]] || fail "unsafe deploy host"
for command in git scp ssh curl; do
  command -v "$command" >/dev/null 2>&1 || fail "required command is missing: $command"
done
cd "$boot_root"
bash scripts/check-dev-deployment.sh
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] \
  || fail "origin bootstrap requires a clean Git worktree"
commit="$(git rev-parse HEAD)"
git fetch origin dev
[[ "$commit" == "$(git rev-parse origin/dev)" ]] \
  || fail "origin bootstrap requires HEAD to equal pushed origin/dev"

short_commit="${commit:0:12}"
remote_http="/tmp/ainer-origin-http-$short_commit-$$.conf"
remote_https="/tmp/ainer-origin-https-$short_commit-$$.conf"
remote_proxy="/tmp/ainer-origin-proxy-$short_commit-$$.conf"
remote_bootstrap="/tmp/ainer-origin-bootstrap-$short_commit-$$.sh"

cleanup() {
  ssh "$deploy_host" \
    "rm -f -- '$remote_http' '$remote_https' '$remote_proxy' '$remote_bootstrap'" \
    >/dev/null 2>&1 || true
}
trap cleanup EXIT

scp ops/dev/nginx/ainer-dev.xiaoqu99.com.http.conf "$deploy_host:$remote_http"
scp ops/dev/nginx/ainer-dev.xiaoqu99.com.conf "$deploy_host:$remote_https"
scp ops/dev/nginx/ainer-boot-dev-proxy.conf "$deploy_host:$remote_proxy"
scp ops/dev/bootstrap-origin.sh "$deploy_host:$remote_bootstrap"
ssh "$deploy_host" \
  "sudo bash '$remote_bootstrap' '$remote_http' '$remote_https' '$remote_proxy'"

curl -fsS --retry 10 --retry-delay 1 \
  https://ainer-dev.xiaoqu99.com/ainer-admin/ >/dev/null
echo "[ainer-origin-bootstrap] HTTPS origin smoke passed"
