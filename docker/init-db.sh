#!/bin/bash
# PostgreSQL 初始化脚本：为 Ainer 双应用创建独立数据库与最小权限账号。
# 由 postgres 容器首次启动时通过 /docker-entrypoint-initdb.d 自动执行。
#
# 密码通过 psql 变量 + :'var' 语法传递，psql 会将其作为加引号的 SQL 字面量安全嵌入，
# 因此密码中的单引号、反斜杠等特殊字符不会破坏 SQL 语句（不同于直接 shell 插值）。
set -e

AINER_DB_PASSWORD="${AINER_DB_PASSWORD:-ainer}"
AINER_AUTH_DB_PASSWORD="${AINER_AUTH_DB_PASSWORD:-ainer_auth}"

# 业务应用数据库（ainer-server）
psql -v ON_ERROR_STOP=1 \
     -v db_password="$AINER_DB_PASSWORD" \
     --username "$POSTGRES_USER" <<-EOSQL
    CREATE ROLE ainer WITH LOGIN PASSWORD :'db_password';
    CREATE DATABASE ainer OWNER ainer;
    GRANT ALL PRIVILEGES ON DATABASE ainer TO ainer;
EOSQL

# Authorization Server 数据库（与业务库隔离，docs/configuration.md 强制策略）
psql -v ON_ERROR_STOP=1 \
     -v auth_db_password="$AINER_AUTH_DB_PASSWORD" \
     --username "$POSTGRES_USER" <<-EOSQL
    CREATE ROLE ainer_auth WITH LOGIN PASSWORD :'auth_db_password';
    CREATE DATABASE ainer_auth OWNER ainer_auth;
    GRANT ALL PRIVILEGES ON DATABASE ainer_auth TO ainer_auth;
EOSQL

echo "[init-db] databases 'ainer' (owner ainer) and 'ainer_auth' (owner ainer_auth) created."
