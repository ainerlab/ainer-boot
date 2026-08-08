#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$boot_root"

scripts=(
  ops/dev/bootstrap-host.sh
  ops/dev/deploy-release.sh
  ops/dev/rollback-release.sh
  ops/dev/bootstrap-origin.sh
  scripts/deploy-authorization-server-dev.sh
  scripts/bootstrap-ainer-dev-origin.sh
)
bash -n "${scripts[@]}"

service=ops/dev/ainer-authorization-server-dev.service
vhost=ops/dev/nginx/ainer-dev.xiaoqu99.com.conf
proxy=ops/dev/nginx/ainer-boot-dev-proxy.conf

grep -Fq 'User=ainer' "$service"
grep -Fq 'EnvironmentFile=/etc/ainer/authorization-server-dev.env' "$service"
grep -Fq '127.0.0.1:19000' "$proxy"
grep -Fq 'proxy_set_header X-Forwarded-For $remote_addr;' "$proxy"
grep -Fq 'access_log off;' "$proxy"
grep -Fq 'alias /opt/ainer-admin/current/' "$vhost"
grep -Fq 'alias /opt/ainer-studio/current/' "$vhost"
grep -Fq 'location = /ainer-login/tokens.css' "$vhost"
grep -Fq 'location = /ainer-login/login.css' "$vhost"
grep -Fq 'location = /default-ui.css' "$vhost"
grep -Fq 'location = /favicon.ico' "$vhost"
grep -Fq 'location = /api/me/access-token-revocations' "$vhost"
grep -Fq "curl --noproxy '*'" ops/dev/bootstrap-origin.sh
grep -Fq -- '--resolve "$domain:443:127.0.0.1"' ops/dev/bootstrap-origin.sh
grep -Fq "curl --noproxy '*'" scripts/bootstrap-ainer-dev-origin.sh

if grep -Eq 'location[[:space:]]+(\^~[[:space:]]+)?/api/' "$vhost"; then
  echo '[ainer-dev-check] broad /api/ proxy is forbidden' >&2
  exit 1
fi
if grep -Eq 'location[[:space:]]+\^~[[:space:]]+/ainer-login/' "$vhost"; then
  echo '[ainer-dev-check] broad /ainer-login/ proxy is forbidden' >&2
  exit 1
fi
if grep -Eq '/opt/xiaoqu|xq-postgres|server_name[[:space:]]+dev\.xiaoqu99\.com;' \
  ops/dev/*.sh ops/dev/*.service ops/dev/nginx/* \
  scripts/deploy-authorization-server-dev.sh scripts/bootstrap-ainer-dev-origin.sh; then
  echo '[ainer-dev-check] Ainer deployment must not depend on xiaoqu runtime paths' >&2
  exit 1
fi

echo '[ainer-dev-check] deployment tooling passed'
