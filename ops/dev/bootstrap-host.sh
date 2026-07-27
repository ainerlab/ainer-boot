#!/usr/bin/env bash
set -euo pipefail

service_unit="${1:-}"
postgres_image="${AINER_AUTH_POSTGRES_IMAGE:-postgres:18.3-alpine}"
postgres_container="ainer-auth-postgres-dev"
postgres_volume="ainer-auth-postgres-dev-data"
config_root="/etc/ainer"
runtime_env="$config_root/authorization-server-dev.env"
fixture_credentials="$config_root/authorization-server-dev-fixture.credentials"
postgres_password_file="$config_root/authorization-server-dev-postgres.password"
key_root="$config_root/authorization-server-dev-keys"
deploy_root="/opt/ainer-boot/authorization-server"

fail() {
  echo "[ainer-boot-bootstrap] ERROR: $*" >&2
  exit 1
}

[[ "$(id -u)" -eq 0 ]] || fail "bootstrap-host.sh must run as root"
[[ "$service_unit" =~ ^/[A-Za-z0-9._/-]+$ && "$service_unit" != *".."* ]] \
  || fail "service unit path is unsafe"
[[ -f "$service_unit" ]] || fail "service unit is missing"

for command in docker openssl install systemctl; do
  command -v "$command" >/dev/null 2>&1 || fail "required command is missing: $command"
done

if ! id ainer >/dev/null 2>&1; then
  useradd --system --home-dir /nonexistent --shell /usr/sbin/nologin ainer
fi

install -d -o root -g ainer -m 0750 "$config_root" "$key_root"
install -d -o root -g ainer -m 0750 "$deploy_root" "$deploy_root/releases"

umask 077
if [[ ! -f "$postgres_password_file" ]]; then
  openssl rand -hex 32 >"$postgres_password_file"
fi
chown root:root "$postgres_password_file"
chmod 0600 "$postgres_password_file"

if [[ ! -f "$fixture_credentials" ]]; then
  owner_password="$(openssl rand -hex 24)"
  member_password="$(openssl rand -hex 24)"
  {
    printf 'AINER_ADMIN_DEV_OWNER_USERNAME=%s\n' 'owner@ainer-dev.test'
    printf 'AINER_ADMIN_DEV_OWNER_PASSWORD=%s\n' "$owner_password"
    printf 'AINER_ADMIN_DEV_MEMBER_USERNAME=%s\n' 'member@ainer-dev.test'
    printf 'AINER_ADMIN_DEV_MEMBER_PASSWORD=%s\n' "$member_password"
  } >"$fixture_credentials"
fi
chown root:root "$fixture_credentials"
chmod 0600 "$fixture_credentials"

if [[ ! -f "$key_root/private.pem" || ! -f "$key_root/public.pem" ]]; then
  [[ ! -e "$key_root/private.pem" && ! -e "$key_root/public.pem" ]] \
    || fail "signing key pair is only partially present"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
    -out "$key_root/private.pem" >/dev/null 2>&1
  openssl pkey -in "$key_root/private.pem" -pubout \
    -out "$key_root/public.pem" >/dev/null 2>&1
fi
chown root:ainer "$key_root/private.pem" "$key_root/public.pem"
chmod 0640 "$key_root/private.pem" "$key_root/public.pem"

if [[ ! -f "$runtime_env" ]]; then
  # shellcheck disable=SC1090
  source "$fixture_credentials"
  postgres_password="$(<"$postgres_password_file")"
  {
    printf 'SPRING_PROFILES_ACTIVE=dev\n'
    printf 'SERVER_ADDRESS=127.0.0.1\n'
    printf 'SERVER_FORWARD_HEADERS_STRATEGY=framework\n'
    printf 'SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/ainer_auth_dev\n'
    printf 'SPRING_DATASOURCE_USERNAME=ainer_auth_dev\n'
    printf 'SPRING_DATASOURCE_PASSWORD=%s\n' "$postgres_password"
    printf 'AINER_AUTHORIZATION_SERVER_PORT=19000\n'
    printf 'AINER_AUTHORIZATION_SERVER_ISSUER=https://ainer-dev.xiaoqu99.com\n'
    printf 'AINER_AUTHORIZATION_SERVER_AUDIENCE=ainer-api\n'
    printf 'AINER_AUTHORIZATION_SIGNING_KEY_ID=ainer-admin-dev-1\n'
    printf 'AINER_AUTHORIZATION_PRIVATE_KEY_LOCATION=file:%s/private.pem\n' "$key_root"
    printf 'AINER_AUTHORIZATION_PUBLIC_KEY_LOCATION=file:%s/public.pem\n' "$key_root"
    printf 'AINER_ADMIN_BROWSER_CLIENT_ENABLED=true\n'
    printf 'AINER_ADMIN_BROWSER_CLIENT_REDIRECT_URI=https://ainer-dev.xiaoqu99.com/ainer-admin/auth/callback\n'
    printf 'AINER_ADMIN_BROWSER_CLIENT_POST_LOGOUT_REDIRECT_URI=https://ainer-dev.xiaoqu99.com/ainer-admin/auth/logged-out\n'
    printf 'AINER_ADMIN_DEV_BOOTSTRAP_ENABLED=true\n'
    printf 'AINER_ADMIN_DEV_OWNER_USERNAME=%s\n' "$AINER_ADMIN_DEV_OWNER_USERNAME"
    printf 'AINER_ADMIN_DEV_OWNER_PASSWORD=%s\n' "$AINER_ADMIN_DEV_OWNER_PASSWORD"
    printf 'AINER_ADMIN_DEV_OWNER_DISPLAY_NAME=Dev Owner\n'
    printf 'AINER_ADMIN_DEV_MEMBER_USERNAME=%s\n' "$AINER_ADMIN_DEV_MEMBER_USERNAME"
    printf 'AINER_ADMIN_DEV_MEMBER_PASSWORD=%s\n' "$AINER_ADMIN_DEV_MEMBER_PASSWORD"
    printf 'AINER_ADMIN_DEV_MEMBER_DISPLAY_NAME=Existing Dev User\n'
  } >"$runtime_env"
fi
chown root:ainer "$runtime_env"
chmod 0640 "$runtime_env"

if docker container inspect "$postgres_container" >/dev/null 2>&1; then
  actual_image="$(docker container inspect --format '{{.Config.Image}}' "$postgres_container")"
  [[ "$actual_image" == "$postgres_image" ]] \
    || fail "existing $postgres_container uses unexpected image: $actual_image"
  docker start "$postgres_container" >/dev/null
else
  docker volume create "$postgres_volume" >/dev/null
  docker run --detach \
    --name "$postgres_container" \
    --restart unless-stopped \
    --publish 127.0.0.1:55432:5432 \
    --env POSTGRES_DB=ainer_auth_dev \
    --env POSTGRES_USER=ainer_auth_dev \
    --env POSTGRES_PASSWORD_FILE=/run/secrets/postgres-password \
    --env POSTGRES_INITDB_ARGS=--data-checksums \
    --mount "type=volume,source=$postgres_volume,target=/var/lib/postgresql" \
    --mount "type=bind,source=$postgres_password_file,target=/run/secrets/postgres-password,readonly" \
    "$postgres_image" >/dev/null
fi

postgres_ready=false
for _ in {1..90}; do
  if docker exec "$postgres_container" pg_isready -U ainer_auth_dev -d ainer_auth_dev \
    >/dev/null 2>&1; then
    postgres_ready=true
    break
  fi
  sleep 1
done
[[ "$postgres_ready" == true ]] || fail "dedicated PostgreSQL did not become ready"

install -o root -g root -m 0644 "$service_unit" \
  /etc/systemd/system/ainer-authorization-server-dev.service
systemctl daemon-reload
systemctl enable ainer-authorization-server-dev.service >/dev/null

echo "AINER_BOOT_HOST_BOOTSTRAPPED=true"
echo "AINER_BOOT_INTERNAL_PORT=19000"
echo "AINER_BOOT_POSTGRES_CONTAINER=$postgres_container"
