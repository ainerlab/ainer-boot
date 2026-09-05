# Ainer 公开 Origin 与独立品牌域名规划

> 文档类型：长期运行与品牌资产规划 · 状态：Proposed，尚未部署 · 最近核对：2026-07-27 · 适用版本：`0.1.x`

本文记录 Ainer 从临时开发域名迁移到未来独立品牌域名时必须保持的公开 origin、OAuth/OIDC、
反向代理和切换边界，防止域名取得后重新讨论或误配同一组安全约束。

品牌名称、首选域名和资产取得状态仍以
[ADR-0004](decisions/0004-ainer-brand-and-naming-baseline.md) 为准。本文不表示 Ainer 已经拥有
`ainer.dev`、`ainer.com`、`ainer.cn` 或相应商标；任何示例只有在完成实际注册、权利核验和部署
批准后才能成为对外事实。

## 1. 当前状态

- Ainer Admin 与 Ainer Boot 已在 `https://localhost:5193` 完成真实 HTTPS 同源联合验收；
- `ainer-dev.xiaoqu99.com` 是当前建议的过渡开发主机名，尚未因本文而完成 DNS、证书、代理或部署；
- 当前没有批准的 production 公网 origin；
- `ainer.dev` 仍是 ADR-0004 记录的首选待取得资产，`ainer.com`、`ainer.cn` 只作为未来可能取得
  独立品牌域名时的迁移示例。

## 2. 每个环境一个规范 Origin

Ainer Admin、Authorization Server 协议端点及其 JSON API 在同一环境必须通过同一个规范 HTTPS
origin 暴露。当前不拆分 `admin`、`auth`、`api` 三个浏览器域名，也不以全局 CORS 连接它们。

过渡开发环境建议使用：

```text
https://ainer-dev.xiaoqu99.com
```

该 origin 内按路径分流：

```text
/ainer-admin/**    -> Ainer Admin static/SPA
/.well-known/**    -> Ainer Authorization Server
/oauth2/**         -> Ainer Authorization Server
/login、/login/**  -> Ainer Authorization Server
/error             -> Ainer Authorization Server
/connect/logout    -> Ainer Authorization Server
/api/me/**         -> Ainer Authorization Server
```

（原 `/ainer-studio/** -> Ainer Studio` 开发环境可选路由已随 ADR-0055 退役并从 dev 服务器下线。）

内部 `ainer-authorization-server` 可以继续监听 `127.0.0.1:9000` 或受控服务网络地址，不需要独立
公开域名。代理必须覆盖而不是透传用户提交的 Forwarded headers，并保留 Cookie、`Location`、表单
body 和外部 HTTPS scheme。

采用独立 Ainer 主机名而不是复用 `dev.xiaoqu99.com`，是为了避免现有业务平台的 `/api`、
`/login`、`/error` 和 SPA fallback 与 Ainer 协议端点发生路径冲突。

## 3. 取得独立品牌域名后的推荐布局

假设未来实际取得并批准使用 `ainer.com`，推荐布局为：

```text
https://ainer.com          品牌官网
https://app.ainer.com      production 消费者管理面 + Boot 登录/API
https://dev.ainer.com      非生产联合环境
```

（原 `studio.ainer.com` Ainer Studio 分发站已随 ADR-0055 退役。）

如果最终取得的是其他独立域名，用相同角色替换 `<brand-domain>`，不把示例名称写死进代码：

```text
<brand-domain>             品牌官网
app.<brand-domain>         production Admin + Boot 同源入口
studio.<brand-domain>      Studio（可选）
dev.<brand-domain>         非生产联合环境
```

消费者自建管理面（原 Ainer Admin）若不参与 Admin 登录会话，可以使用独立 origin；管理面与
Boot 登录/API 仍必须共同使用 `app.*` origin。只有出现多个真实独立应用共享统一登录的需求，
并完成 Cookie、CORS、issuer、客户端注册、会话和退出模型 ADR 后，才考虑独立 `auth.*` 域名。

## 4. `ainer.com` 与 `ainer.cn` 的边界

两个品牌域名不能同时被当作同一套登录会话的可互换入口。OIDC issuer、Cookie、redirect URI 和
浏览器存储都绑定精确 origin。

- 如果其中一个只是品牌保护或中文官网入口，应跳转到选定的规范官网域名，不承载 OAuth/Token
  协议；
- 如果 `.cn` 代表独立区域环境，则必须拥有独立的 issuer、browser client、redirect URI、密钥、
  Cookie、运行基础设施和区域验收；
- 不默认让一个区域签发的 Token 被另一区域接受，也不通过跨域 Cookie 共享登录状态；
- 数据驻留、备案、商标和合规结论必须在真实目标地区由专业流程确认，本文不替代法律意见。

## 5. 域名迁移步骤

从临时 origin 迁移到独立品牌域名时按以下顺序执行：

1. 取得域名并保存注册主体、续费、DNS、证书和商标核验记录；
2. 选定唯一规范 production origin 与独立 dev origin；
3. 配置 DNS、受信 HTTPS 证书、HSTS/CSP 和精确反向代理，但暂不切流；
4. 为 production 新建独立 browser client，不能复用 `ainer-admin-dev`；
5. 把 Authorization Server issuer、Admin callback 和 post-logout redirect URI 精确配置为新 origin；
6. 在新 origin 使用真实浏览器重新执行 PKCE、成员管理、revoke、logout、Cookie、缓存和错误门禁；
7. 并行观察完成后切换规范入口，要求现有用户重新登录，不迁移浏览器 Token 或 Cookie；
8. 旧域名仅可对官网或普通静态页面做受控跳转，不对 `/.well-known`、`/oauth2`、`/login`、
   `/connect/logout` 和 Token/API 请求做笼统 `301`；
9. 等待旧 Token TTL、登录会话和回滚窗口结束，再退役旧 client、issuer、证书和代理规则。

迁移主要改变 DNS、证书、issuer、OAuth client、redirect URI 和代理配置。Ainer Admin 使用
`window.location.origin` 解析协议与 API，正常情况下不需要因品牌域名变化重写页面或成员 API。

## 6. 决策与验收门禁

以下事项发生前必须新增或更新正式决策，而不是直接修改域名字符串：

- 实际购买、注册或对外宣称拥有某个 Ainer 域名或商标；
- production 公开 origin 和托管区域确定；
- 把 Authorization Server 拆到独立浏览器 origin；
- 同时运行多个可签发人员 Token 的区域 issuer；
- 让旧域名与新域名长期并行，而不是限时迁移；
- 在新域名启用 production client、真实用户或生产数据。

每次域名切换至少记录：域名所有权验证记录、DNS/TLS 配置、公开 issuer、client ID、回调地址、代理
清单、联合 E2E commit、切换时间、批准人、回滚窗口和旧入口退役结果。
