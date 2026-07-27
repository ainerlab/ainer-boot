# Ainer 开发环境部署与跨 Session 交接

> 文档类型：dev 运行手册与时间敏感交接 · 状态：公网开发环境与真实浏览器验收已完成 · 最近核对：2026-07-27

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
├── /ainer-login/tokens.css
├── /ainer-login/login.css
├── /default-ui.css      仅供回滚到 M6 之前的 Boot release
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
pnpm deploy:admin:dev --skip-http-smoke

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

品牌登录页静态资源只允许两个精确代理：
`/ainer-login/tokens.css` 与 `/ainer-login/login.css`。不得增加
`location ^~ /ainer-login/` 或通用静态目录代理。`/default-ui.css` 只保留给 M6 之前的
Authorization Server 回滚版本，新页面不引用它。

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

截至 2026-07-27 最近核对：

- 规范 origin `https://ainer-dev.xiaoqu99.com` 已解析到 `43.139.111.228`；Let's Encrypt
  证书只有 `DNS:ainer-dev.xiaoqu99.com`，有效期至 `2026-10-25 01:43:26 UTC`，certbot timer
  负责续期；
- `ainer-authorization-server-dev.service` 为 `active/running`，只监听
  `127.0.0.1:19000`；独立 `ainer-auth-postgres-dev` 为 `running`，使用
  `postgres:18.3-alpine` 与 `127.0.0.1:55432`，首次空库已成功执行 16 份 migration；
- Boot current 为 `3f9420a4425f-20260727024015`，commit
  `3f9420a4425f11e78feace776fe0b15853a0b884`，JAR SHA-256
  `a8709bbee9e6916e5654ac3ee221c04747f1c8cba97457a4b1e0d8a3ca1c6165`；
- Studio current 为 `d13fe026cd54-20260727025810`，Admin current 为
  `d13fe026cd54-20260727025626`；二者都绑定
  `d13fe026cd5422f85f03c443e09f825c05e114a1`；
- 两个仓库均已创建并推送 annotated tag `dev-env-20260727`：Boot 标签精确指向上述 Boot
  runtime commit，Studio 标签精确指向上述 Studio/Admin runtime commit。需要回看首次公网验收
  代码时使用标签，不要用后续纯文档 HEAD 替代运行制品；
- 服务器当前 Nginx/origin 配置已安装至 Boot `ab32325` 基线；`0f27e73`、`4cd2963`
  只修正公网/本机 SNI smoke，`ab32325` 补齐登录页样式与 favicon 精确路由，这些提交都不要求
  重发当前 JAR；
- fixture 第一次初始化成功后已关闭，密码已从 Java EnvironmentFile 移除；root-only
  `/etc/ainer/authorization-server-dev-fixture.credentials` 仅供 dev 登录与联合验收，不得输出；
- 真实 remote Chromium 已在上述 releases 上通过 PKCE、表单登录、成员列表/添加、双向角色调整、
  软移除、当前 access token revoke、OIDC logout 和退出后重新访问要求登录；测试没有网络拦截、
  HAR、fetch mock、伪造 Token 或 mock 降级；
- 公网测试曾暴露 Studio 的退出竞态：发起 `/connect/logout` 后过早清空界面会话会触发路由守卫
  抢先重新授权。Studio `d13fe02` 已修复并补充安全网络诊断与回归测试，复验通过。
- Spring Security 默认登录页依赖的精确 `/default-ui.css` 已转发到 Boot；根
  `/favicon.ico` 由 Nginx 返回空响应，避免页面样式缺失和无意义的控制台 404。没有因此增加
  宽泛静态目录或 `/api` 代理。

M6 品牌登录候选尚未部署。候选代码将新增精确 `/ainer-login/tokens.css` 与
`/ainer-login/login.css` 代理；发布后必须先确认 `GET /login` 的合同版本与资源均来自同一 Boot
release，再通知 Studio 重跑第 6 节 remote E2E。当前线上仍是已验收的 Spring Security 默认登录页，
不得把本地候选误写成已上线事实。

Studio 登录视觉合同 1.0.0 明确不显示 Passkey 动作。M6 候选保留既有 WebAuthn 端点、条件 MFA
过滤器和 `factor.type` / `factor.reason` 表单上下文，但在 Studio 发布包含 Passkey 交互的新合同前，
不得在需要人员可见 Passkey 登录的环境启用该候选。开发环境当前 Passkey 默认关闭。

后续 Boot、Studio 或其他 session 不要重新运行首次 bootstrap，也不要复用小趣 PostgreSQL。先执行
第 4 节只读检查并读取三个 `current/release.json`；变更后只发布所属 release，最后重跑第 6 节
真实公网门禁。若 release 与本文不同，以服务器签名/清单和当次验收结果为准，并立即更新本节。
