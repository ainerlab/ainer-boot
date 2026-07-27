# Ainer 开发环境部署与跨 Session 交接

> 文档类型：dev 运行手册与时间敏感交接 · 状态：部署工具已建立，公网验收待执行 · 最近核对：2026-07-27

本文是 `ainer-dev.xiaoqu99.com` 联合开发环境的操作入口。Boot、Studio 或其他 session 接手前先读
本页，再分别阅读 [`ainer-admin-integration.md`](ainer-admin-integration.md) 与
[`public-origin-and-domain-strategy.md`](public-origin-and-domain-strategy.md)。本文只适用于 dev，
不构成 production SOP。

## 1. 固定拓扑

```text
https://ainer-dev.xiaoqu99.com
├── /ainer-admin/**     /opt/ainer-admin/current          Ainer Admin Boot-mode SPA
├── /ainer-studio/**    /opt/ainer-studio/current         Ainer Studio + mock preview
├── /.well-known/**
├── /oauth2/**
├── /login、/login/**
├── /error
├── /connect/logout
├── /api/me/access-token-revocations
└── /api/tenants/{tenantId}/members/**
                        127.0.0.1:19000                   Authorization Server

ainer-authorization-server-dev.service
└── jdbc:postgresql://127.0.0.1:55432/ainer_auth_dev
    └── ainer-auth-postgres-dev / postgres:18.3-alpine
```

内部端口 `9000` 已被该主机上的 Portainer 占用，因此 dev 固定使用 `19000`。Ainer 使用独立
PostgreSQL 容器、volume、用户和数据库，不读写小趣的 `xq-postgres`。Nginx 使用独立
`ainer-dev.xiaoqu99.com.conf`，不覆盖 `dev.xiaoqu99.com`。

## 2. 仓库与服务器职责

| 项目 | 权威位置 |
|---|---|
| Authorization Server 构建、systemd、PostgreSQL bootstrap、Nginx origin | Ainer Boot |
| Ainer Admin 唯一源码、Boot-mode 静态制品、Ainer Studio 静态制品 | Ainer Studio |
| Boot release | `/opt/ainer-boot/authorization-server/releases/` |
| Boot current | `/opt/ainer-boot/authorization-server/current` |
| Admin release | `/opt/ainer-admin/releases/` |
| Studio release | `/opt/ainer-studio/releases/` |
| 运行配置 | `/etc/ainer/authorization-server-dev.env`，`root:ainer 0640` |
| RSA key | `/etc/ainer/authorization-server-dev-keys/`，不进入 Git |
| dev fixture 凭据 | `/etc/ainer/authorization-server-dev-fixture.credentials`，`root:root 0600` |
| TLS | `/etc/letsencrypt/live/ainer-dev.xiaoqu99.com/`，由 certbot timer 续期 |

fixture 密码只在第一次严格幂等初始化时进入 Java 进程；成功后部署脚本关闭 fixture 并从运行
EnvironmentFile 删除密码。root-only 凭据文件仅供 dev 联合验收和维护者登录，不得复制到文档、
Git、聊天、日志或普通配置。

## 3. 首次部署顺序

所有发布命令都要求干净 worktree，并要求本地 HEAD 精确等于已推送的权威远端分支。

```bash
# 0. 本地校验脚本、隔离路径和精确代理边界。
cd /Users/xq/01-code/xq/ainer-boot
bash scripts/check-dev-deployment.sh

# 1. Ainer Boot：创建独立 PostgreSQL、密钥、systemd，并发布 JAR。
bash scripts/deploy-authorization-server-dev.sh --skip-public-smoke

# 2. Ainer Studio：先发布 Studio，再发布官方 Boot-mode Admin。
cd /Users/xq/01-code/xq/ainer-studio
pnpm deploy:dev
pnpm deploy:admin:dev -- --skip-http-smoke

# 3. Ainer Boot：签发 TLS，安装独立 vhost 和精确同源代理。
cd /Users/xq/01-code/xq/ainer-boot
bash scripts/bootstrap-ainer-dev-origin.sh
```

之后日常后端部署只运行：

```bash
bash scripts/deploy-authorization-server-dev.sh
```

脚本上传本机构建的可执行 JAR，服务器不持有 GitHub 私钥；服务器在持锁状态下建立版本化 release、
原子切换 `current`、重启 systemd、验证 issuer，并只保留最近 5 个 release。

## 4. 只读检查

```bash
ssh ubuntu@dev.xiaoqu99.com \
  "systemctl is-active ainer-authorization-server-dev.service"
ssh ubuntu@dev.xiaoqu99.com \
  "docker inspect --format '{{.State.Status}}' ainer-auth-postgres-dev"
ssh ubuntu@dev.xiaoqu99.com \
  "sudo cat /opt/ainer-boot/authorization-server/current/release.json"
curl -fsS https://ainer-dev.xiaoqu99.com/.well-known/openid-configuration
curl -fsS https://ainer-dev.xiaoqu99.com/ainer-admin/release.json
curl -fsS https://ainer-dev.xiaoqu99.com/ainer-studio/release.json
```

日志只通过 journald 查看，不记录 Token、授权码、密码或完整协议 URL：

```bash
ssh ubuntu@dev.xiaoqu99.com \
  "sudo journalctl -u ainer-authorization-server-dev.service -n 100 --no-pager"
```

Nginx 已对 OAuth、logout 与成员 API location 关闭 access log，避免 query 中的授权码或
`id_token_hint` 进入通用日志。

## 5. 回滚

先只读选择 release：

```bash
ssh ubuntu@dev.xiaoqu99.com \
  "sudo find /opt/ainer-boot/authorization-server/releases -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -r"
```

取得明确 release id 后，把仓内回滚脚本上传并以 root 运行：

```bash
scp ops/dev/rollback-release.sh ubuntu@dev.xiaoqu99.com:/tmp/ainer-boot-rollback.sh
ssh ubuntu@dev.xiaoqu99.com \
  "sudo bash /tmp/ainer-boot-rollback.sh <12位commit>-<UTC时间戳>"
```

回滚会核对 JAR SHA-256、持有部署锁、原子切换 symlink、重启并验证固定 issuer。Migration 只前进，
回滚应用前必须确认旧 JAR 与当前 schema 兼容；不得删除数据库、volume、授权记录或 migration。

## 6. 联合验收门禁

公网部署不以首页 200 作为完成。必须从 Ainer Studio 运行无网络拦截的真实 Chromium 链路：

```bash
AINER_ADMIN_REMOTE_BASE_URL=https://ainer-dev.xiaoqu99.com \
AINER_ADMIN_E2E_OWNER_USERNAME='<从 root-only 凭据安全注入>' \
AINER_ADMIN_E2E_OWNER_PASSWORD='<从 root-only 凭据安全注入>' \
AINER_ADMIN_E2E_MEMBER_USERNAME='<从 root-only 凭据安全注入>' \
AINER_ADMIN_BOOT_ACCEPTANCE_HEAD='<服务器 release.json 的完整 Boot commit>' \
pnpm test:e2e:boot:remote
```

验收覆盖 PKCE、表单登录、成员列表、添加已有用户、ADMIN/MEMBER 双向调整、软移除、当前 Token
撤销、OIDC logout 和再次要求登录。失败时不得降级 mock 或开启 CORS。

## 7. 当前交接状态

截至本文最近核对：

- DNS `ainer-dev.xiaoqu99.com` 已解析到 `43.139.111.228`；
- Boot/Studio 阶段 5 本地联合验收基线仍为 Boot `ea30ff43...` 与 Studio `e217154...`；
- 部署、回滚、systemd、独立 PostgreSQL 和 Nginx 配置已进入仓库，服务器变更尚未执行；
- 首次公网成功后必须在本节记录实际 Boot/Studio/Admin commit、证书、服务状态和真实浏览器证据，
  不得由后续 session 根据“有脚本”推断“已部署”。
