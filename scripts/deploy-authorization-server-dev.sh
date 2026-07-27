#!/usr/bin/env bash
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
deploy_host="${AINER_BOOT_DEPLOY_HOST:-ubuntu@dev.xiaoqu99.com}"
skip_public_smoke=false

fail() {
  echo "[ainer-boot-deploy] ERROR: $*" >&2
  exit 1
}

if [[ "${1:-}" == "--skip-public-smoke" && "$#" -eq 1 ]]; then
  skip_public_smoke=true
elif [[ "$#" -ne 0 ]]; then
  fail "usage: scripts/deploy-authorization-server-dev.sh [--skip-public-smoke]"
fi

[[ "$deploy_host" =~ ^[A-Za-z0-9._-]+@[A-Za-z0-9._-]+$ ]] || fail "unsafe deploy host"
for command in git mvn node scp ssh curl; do
  command -v "$command" >/dev/null 2>&1 || fail "required command is missing: $command"
done

cd "$boot_root"
bash scripts/check-dev-deployment.sh
[[ -z "$(git status --porcelain --untracked-files=normal)" ]] \
  || fail "deployment requires a clean Git worktree"
commit="$(git rev-parse HEAD)"
git fetch origin dev
origin_dev="$(git rev-parse origin/dev)"
[[ "$commit" == "$origin_dev" ]] \
  || fail "deployment commit must equal pushed origin/dev (local=$commit remote=$origin_dev)"

mvn -pl ainer-authorization-server -am package -DskipTests
jar="$(find "$boot_root/ainer-authorization-server/target" -maxdepth 1 -type f \
  -name 'ainer-authorization-server-*.jar' ! -name '*.original' | sort | tail -1)"
[[ -n "$jar" && -f "$jar" ]] || fail "Authorization Server executable JAR was not found"

temporary_dir="$(mktemp -d)"
short_commit="${commit:0:12}"
remote_jar="/tmp/ainer-authorization-server-$short_commit-$$.jar"
remote_bootstrap="/tmp/ainer-boot-bootstrap-$short_commit-$$.sh"
remote_deploy="/tmp/ainer-boot-deploy-$short_commit-$$.sh"
remote_unit="/tmp/ainer-authorization-server-$short_commit-$$.service"

cleanup() {
  rm -rf -- "$temporary_dir"
  ssh "$deploy_host" \
    "rm -f -- '$remote_jar' '$remote_bootstrap' '$remote_deploy' '$remote_unit'" \
    >/dev/null 2>&1 || true
}
trap cleanup EXIT

scp "$jar" "$deploy_host:$remote_jar"
scp "$boot_root/ops/dev/bootstrap-host.sh" "$deploy_host:$remote_bootstrap"
scp "$boot_root/ops/dev/deploy-release.sh" "$deploy_host:$remote_deploy"
scp "$boot_root/ops/dev/ainer-authorization-server-dev.service" "$deploy_host:$remote_unit"
ssh "$deploy_host" "sudo bash '$remote_bootstrap' '$remote_unit'"
ssh "$deploy_host" "sudo bash '$remote_deploy' '$remote_jar' '$commit' '5'"

if [[ "$skip_public_smoke" == false ]]; then
  discovery="$(curl -fsS --retry 10 --retry-delay 1 \
    https://ainer-dev.xiaoqu99.com/.well-known/openid-configuration)"
  printf '%s' "$discovery" | node -e '
    let input = ""
    process.stdin.setEncoding("utf8")
    process.stdin.on("data", (chunk) => { input += chunk })
    process.stdin.on("end", () => {
      const value = JSON.parse(input)
      if (value.issuer !== "https://ainer-dev.xiaoqu99.com") process.exit(1)
    })
  '
  echo "[ainer-boot-deploy] public discovery smoke passed"
fi
