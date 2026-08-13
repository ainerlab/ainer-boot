#!/usr/bin/env bash
# 生成 Ainer Authorization Server 开发用 RSA 3072 签名密钥对。
#
# 密钥格式（与 PemRsaKeyLoader 契约一致）：
#   - 私钥：PKCS#8 PEM（-----BEGIN PRIVATE KEY-----），openssl genpkey 直接产出此格式
#   - 公钥：X.509 SubjectPublicKeyInfo PEM（-----BEGIN PUBLIC KEY-----）
#   - 密钥长度：3072 位（与 ops/dev/bootstrap-host.sh 一致）
#
# 产出路径：secrets/dev-keys/private.pem、secrets/dev-keys/public.pem
# 该路径已被 .gitignore 覆盖（secrets/ 与 *.pem），不会误提交。
set -euo pipefail

boot_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
key_dir="$boot_root/secrets/dev-keys"

mkdir -p "$key_dir"

if [[ -f "$key_dir/private.pem" && -f "$key_dir/public.pem" ]]; then
  echo "[dev-keys] RSA key pair already exists at $key_dir — skipping."
  exit 0
fi

# 原子性检查：不允许只有一半密钥存在
if [[ -e "$key_dir/private.pem" || -e "$key_dir/public.pem" ]]; then
  echo "[dev-keys] ERROR: key pair is only partially present in $key_dir" >&2
  echo "[dev-keys] Remove the leftover file and re-run, or delete the whole directory." >&2
  exit 1
fi

echo "[dev-keys] Generating RSA 3072 key pair in $key_dir ..."
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out "$key_dir/private.pem"
openssl pkey -in "$key_dir/private.pem" -pubout \
  -out "$key_dir/public.pem"
chmod 600 "$key_dir/private.pem"
chmod 644 "$key_dir/public.pem"

echo "[dev-keys] Done. Private: $key_dir/private.pem (0600)"
echo "[dev-keys]        Public: $key_dir/public.pem (0644)"
echo ""
echo "Configure Authorization Server:"
echo "  AINER_AUTHORIZATION_PRIVATE_KEY_LOCATION=file:$key_dir/private.pem"
echo "  AINER_AUTHORIZATION_PUBLIC_KEY_LOCATION=file:$key_dir/public.pem"
