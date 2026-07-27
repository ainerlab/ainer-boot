#!/usr/bin/env bash
set -euo pipefail

http_config="${1:-}"
https_config="${2:-}"
proxy_snippet="${3:-}"
domain="ainer-dev.xiaoqu99.com"
target_config="/etc/nginx/conf.d/$domain.conf"
target_snippet="/etc/nginx/snippets/ainer-boot-dev-proxy.conf"

fail() {
  echo "[ainer-origin-bootstrap] ERROR: $*" >&2
  exit 1
}

[[ "$(id -u)" -eq 0 ]] || fail "bootstrap-origin.sh must run as root"
for file in "$http_config" "$https_config" "$proxy_snippet"; do
  [[ "$file" =~ ^/[A-Za-z0-9._/-]+$ && "$file" != *".."* && -f "$file" ]] \
    || fail "required configuration is missing or unsafe: $file"
done
[[ -f /opt/ainer-studio/current/index.html ]] || fail "Ainer Studio release is missing"
[[ -f /opt/ainer-admin/current/index.html ]] || fail "Ainer Admin release is missing"
install -d -o root -g root -m 0755 /var/www/certbot /etc/nginx/snippets

backup=""
if [[ -f "$target_config" ]]; then
  backup="$target_config.bak.$(date -u +%Y%m%d%H%M%S)"
  cp -a "$target_config" "$backup"
fi

restore_on_error() {
  if [[ -n "$backup" && -f "$backup" ]]; then
    cp -a "$backup" "$target_config"
  else
    rm -f "$target_config"
  fi
  nginx -t >/dev/null 2>&1 && systemctl reload nginx || true
}
trap restore_on_error ERR

install -o root -g root -m 0644 "$http_config" "$target_config"
nginx -t
systemctl reload nginx

if [[ ! -f "/etc/letsencrypt/live/$domain/fullchain.pem" ]]; then
  certbot certonly \
    --webroot \
    --webroot-path /var/www/certbot \
    --cert-name "$domain" \
    --domains "$domain" \
    --non-interactive \
    --agree-tos \
    --keep-until-expiring
fi
[[ -f "/etc/letsencrypt/live/$domain/fullchain.pem" ]] || fail "TLS certificate is missing"

install -o root -g root -m 0644 "$proxy_snippet" "$target_snippet"
install -o root -g root -m 0644 "$https_config" "$target_config"
nginx -t
systemctl reload nginx
trap - ERR

curl --noproxy '*' -fsS --retry 10 --retry-delay 1 \
  "https://$domain/.well-known/openid-configuration" >/dev/null
curl --noproxy '*' -fsS --retry 10 --retry-delay 1 \
  "https://$domain/ainer-admin/" >/dev/null
curl --noproxy '*' -fsS --retry 10 --retry-delay 1 \
  "https://$domain/ainer-studio/" >/dev/null
echo "AINER_DEV_ORIGIN_BOOTSTRAPPED=https://$domain"
