# Ainer 项目状态

> 文档类型：时间敏感快照 · 状态：持续更新 · 核对时间：2026-08-18 · 工程版本：`1.0.0`（已发布——**1.0 达成**）

本文只记录当前事实和验证记录，不替代架构规范与 ADR。每个里程碑结束、发布候选形成或主要风险变化时更新核对时间。

## 1. 当前阶段

`reset/0033-greenfield` 分支已按 ADR-0033（Option B：完全移除 Tenant）完成 S1–S8 全部施工序列
并全绿验证（205 tests / 0 failure / 0 error / 0 skipped）：Identity 换为 HumanAccount/
ServicePrincipal/LoginIdentity/Credential foundation，Token 使用 typed `token_profile`
（`SERVICE_V1`/`USER_NEUTRAL_V1`）与 `claim_contract_version=1`，撤销通过
`security_epoch`/`sec_epoch` claim 在线比对；Workspace 与 AI Runtime 已去 tenant 化，改为纯
membership/subject 边界；legacy identity、tenant 上下文、access-event outbox/relay 消费链路与
平台预配通知均已删除，migration 重建为 4 个可空库重放的 standalone baseline
（identity=`V202608070300`、workspace=`V202608070310`、ai-runtime=`V202608070320`、
authorization-server=`V202608070330`）。M6 品牌 `/login`（Studio 视觉合同 1.0.0）已由
Authorization Server 承载并于 2026-07-29 部署 dev (release `e6cb0b44bb9e-20260729053046`)，
真实 remote 联合 E2E 通过，Studio 合同状态 `implemented`。

下文 M4.x 里程碑记录中涉及 tenant、access-event、relay、Directory 与平台预配的历史描述反映
当时代码，已被 Greenfield S8 删除取代，不作为当前部署依据。真实外部通知网关/供应商联调、供应商
回执映射、最终送达验证、生产限速/告警尚未完成，0-skipped 仍需在正式发布候选环境重复执行。
当前工程是可编译、可运行、可用真实 PostgreSQL 验证的 Spring Boot 4.1 多模块基线，但尚未达到
生产或商业发行就绪。

P2 Create & Generate 的原始能力批次已收口（2026-08-09）：`ainer-initializer`（Manifest v1
解析/校验 + 确定性生成内核）与 `ainer-initializer-cli`（preview/init/diff 离线命令）已交付并通过
`verify-initializer-consumer.sh` 三通道门禁（两轮生成字节一致 diff、普通/postgres/CRUD 三个
变体真实测试均 0 skipped、无 Ainer 框架源码副本、生成项目独立编译）；ADR-0035 与 ADR-0036 均
Accepted。P2 退出门禁逐项验证：确定性（同 manifest 两轮 diff=0）、生成安全（preview 不落盘、
非空目标拒绝覆盖、生成器无数据库/网络写入、默认不改菜单）、TTFR 实测 100s（门禁 600s）、
TTCRUD 实测 124s（门禁 1800s）、生成物通过 PostgreSQL 与 golden consumer 门禁。
组织/行业模板与策略包是 ADR-0035 决策 7 明示的 v1 非目标，属 Studio/Enterprise 扩展
（设计文档能力矩阵第 94 行），移交 P3+ 扩展清单，不再作为 P2 阻塞项。

2026-08-13 首个产品消费者 `xq-platform-next` 复核发现 `v0.1.0-rc.2` 的 Initializer 存在一个
真实合同缺口：README 与 ADR-0035 决策 6 要求 `./mvnw`，生成树却没有 Wrapper；此前门禁借用了
Ainer 生产者仓库的 Maven 4 Wrapper，因而“生成项目独立构建”的证据不完整。当前开发分支已补入
Apache Maven Wrapper 3.3.4、固定 Maven 3.9.16 官方发行包与 SHA-256、POSIX 执行位写入/diff，
并让 consumer/TTFR/TTCRUD 使用生成项目自己的 Wrapper。定向测试与独立 Wrapper 冒烟已通过；
完整 reactor、三通道消费者、TTFR 与 TTCRUD 本地门禁也已通过。修复已进入已发布候选
`v0.1.0-rc.3`；远端 Initializer CLI 生成的三个项目均携带并实际使用自己的 Maven 3.9.16 Wrapper，
P2 的已发布实现合同已重新关闭。`rc.2` 保持不可变，只作为升级/回滚的已发布起点；G2 仍要求
`xq-platform-next` 完成 `rc.2 -> rc.3 -> rc.2` 的真实产品升级/回滚与 migration replay。

2026-08-12 P3 企业基建首批代码完成：文件存储 SPI、字典、配置、通知、缓存 starter、Spring Cache
改造。新建 5 个模块（ainer-module-dictionary/config/notification + ainer-starter-cache + 文件存储
SPI），全部装配到 ainer-server。ADR-0039 Accepted；ADR-0038 已因决策维护违规被 ADR-0040 合规取代。

2026-08-14 文件存储模块 `ainer-module-file` 交付（G1「文件元数据补齐」项关闭）：新建第 24 个
reactor 模块，含 `ainer_file_object`/`ainer_file_audit` migration（UUIDv7 CHECK）、上传/下载/删除
与分页管理 API（`/api/files`，`file.read`/`file.write` scope，413/415 真实状态码）、大小/类型
限制、SHA-256 校验、上传失败补偿与同事务变更审计；装配进 ainer-server 并同步发布链
（BOM、release-artifacts 24 projects/112 primary、consumer 24 POM）。服务层 11 + 真签名 JWT
HTTP 6 项 Testcontainers 测试全绿。

2026-08-14 P3 三模块服务端管理 API 交付（G1「管理 API 补齐」项关闭，**G1 整体关闭**）：
dictionary/config/notification 各自新增稳定错误码枚举（`AINER.<MODULE>.*`，替换裸
IllegalArgumentException）、`*.read`/`*.manage`（notification 另有 `*.submit`）scope 在应用服务
内对已验证 principal 强制、管理 REST API（乐观锁部分更新、动作名词状态变更端点、分页 ≤100）
与同事务变更审计（dictionary/notification 新增 append-only 审计表 `V202608140200/0300`；
config 沿用 `ainer_config_history`）。写入面补齐：字典类型/项的更新与启停、通知模板更新/启停/
分页、投递记录按状态分页（记录列表不回显 title/body，PII）。`ainer-test-support` 新增
`JwtTestSupport` 共享真 JWT fixture（RSA 签发 + 真 JwtDecoder + @Primary resolver 工厂）。
三模块真签名 JWT HTTP 测试（401/403/201/409/审计行/PII 脱敏）全部通过。剩余边界：OpenAPI
运行时文档仍未引入（Boot 4.1 springdoc 兼容性待验证），不阻塞 G1 关闭。

2026-08-14 `v0.1.0` 已成为第一个稳定 `0.1` 基线（**G2 关闭**）：发布准备 PR #9 合入默认分支
（merge `ccd5097`，dev CI run `31784657704` 12m41s 全绿）后，annotated tag `v0.1.0` 与 release
run `31785695252` 全部通过——签名 deploy、112 个主制品/112 个 `.asc` 远端读回验签、Maven 3/Maven 4
空仓消费、远端 Initializer 三通道、SBOM/checksum/provenance 与 immutable GitHub Release
（读回 `immutable=true`、非 draft、非 prerelease、精确绑定 `ccd5097`，16 个签名证据资产）。
发布后 `xq-platform-next` 已完成 `rc.3 → 0.1.0` 真实升级（新隔离冷仓，0.1.0 制品全部远端解析，
14 tests / 0 skipped），连续升级链 `rc.2 → rc.3 → 0.1.0` 全绿。`rc.2`/`rc.3` 保持不可变，作为
升级/回滚链历史起点；`0.1.0` 不是公开发行版、生产就绪或 1.0 声明，许可状态仍为私有/专有。

2026-08-14 G1 三提交（UUIDv7 全域迁移 `ef6714c`、文件存储模块 `00fb77e`、P3 管理 API
`68110ea`）以 PR #8 合入 `dev`（merge commit `050cacb`）：PR 三项检查（quality gate
10m51s、virtual-thread matrix 2m2s、gitleaks）与合并后默认分支 run `31782099908`
（12m12s success）全绿。dev 主线自此包含 ADR-0040 1.0 Product Contract 的全部 P3 基座。

2026-08-14 首个外部消费者 G2 验证完成：本机 GitHub 凭据已具备 `read:packages`，此前阻塞
产品消费的授权缺口解除。`xq-platform-next`（独立仓库，本地 git）依次交付：Maven Wrapper
补齐（对齐 rc.3 生成器合同）；GitHub Packages 仓库与 env 注入认证模板（零密钥入库）；
远端 `v0.1.0-rc.2` 冷仓基线（隔离 `maven.repo.local`，全部制品远端解析，4/0/0/0）；
升级/回滚演练 `rc.2 → rc.3 → rc.2 → 最终固定 rc.3`（每步 4/0/0/0，git 历史保留完整序列）；
产品纵向切片（JWT 安全链、资源授权、V2 migration、真实 HTTP 错误、客户端 SDK，14/0/0/0）。
G2 的消费者证据项全部关闭，剩余唯一动作是发布 `0.1.0` 本体。

2026-08-13 `v0.1.0-rc.3` 已成为当前**合格受控 RC**：annotated tag、不可变 GitHub Release 与默认
分支精确绑定到 merge commit `666b1556f11935925369586152a3791180b7314e`；默认分支 run
`31675092195` 和发布 run `31675920731` 均成功。发布流程完成 338 tests / 0 failure / 0 error /
0 skipped、107 个主制品/107 个 OpenPGP 签名的远端完整读回、Maven 3/Maven 4 空仓消费和从远端
CLI 执行的 Initializer 普通/PostgreSQL/CRUD 三通道；生成项目自带并实际使用锁定 Maven 3.9.16
的 Wrapper。Release 是 prerelease、非 draft、`immutable=true`，含 16 个签名证据资产；发布后
独立下载验证 `EVIDENCE-SHA256SUMS` 的 14 项、全部适用 detached signatures 与精确 fingerprint。

2026-08-13 `v0.1.0-rc.2` 已按 ADR-0041 完成第一个**合格受控 RC**：annotated tag、不可变 GitHub
Release 与默认分支精确绑定到 merge commit `0f99ee08f5d9145bc5bc72052eaf59774aad8054`；默认分支
run `31666240720` 和发布 run `31666957663` 均成功。发布流程完成 336 tests / 0 failure / 0 error /
0 skipped、107 个主制品/107 个 OpenPGP 签名的远端完整读回、Maven 3/Maven 4 空仓消费、Project
Initializer 普通/PostgreSQL/CRUD 远端消费，以及带签名的 SBOM/checksum/provenance 和 immutable
Release。Release 有 16 个 assets；发布后又在隔离 keyring 验证 7 个 detached signatures，并验证
`EVIDENCE-SHA256SUMS` 的 14 项全部通过。GitHub Attestations 因未显式启用而按设计跳过，强制的
Ainer 项目签名 provenance 已通过。

`rc.2` 关闭了 P1 的首个合格受控 RC 发布门禁；`rc.3` 又关闭了 Initializer Wrapper 的已发布合同
缺口，但没有关闭 G2。`xq-platform-next` 仍需以远端 `rc.2` 为升级起点、以 `rc.3` 为最终消费版本，
完成真实产品纵向切片、PostgreSQL migration replay、升级和回滚，之后才能评估稳定 `0.1.0`。
`0.1.0-rc.1` 仍是 **withdrawn / non-qualifying**，禁止消费、复用、移动或覆盖；当前开发版本保持
`0.1.0-SNAPSHOT`。`rc.3` 不是稳定版、公开发行版、生产就绪或 1.0 声明。许可状态仍为私有/专有；
公开发行必须另行完成 LICENSE/NOTICE、品牌资产和对外许可决策。

## 2. 当前已完成能力与历史施工记录

本节保留既有施工轨迹，其中 pre-Greenfield 的 tenant、Directory、access-event/outbox 等条目只描述
当时里程碑，已被 ADR-0033 S8 删除，不是 `0.1` 当前支持面。当前结论优先以上述阶段说明、ADR-0037
和 §4/§5 为准。

- JDK 25、Maven Reactor、独立 BOM 与 Spring Boot 4.1.0 基线；
- 无 Spring 依赖的核心错误和身份参与者契约；
- Web、Persistence、Security Starter 及自动装配测试；
- ADR-0029 T0 第 1 项的 Web Starter 实现范围：`ainer-starter-web` 已从废弃兼容坐标
  `spring-boot-starter-web` 切换为 `spring-boot-starter-webmvc`，并以聚焦的
  `spring-boot-starter-webmvc-test` 替代该模块原先手工拼装的测试依赖；
- Workspace PostgreSQL 垂直切片、tenant 隔离、成员生命周期、单一 OWNER、授权审计写入与分页读取；
- AI Model Gateway 非流式/SSE、模型白名单、限流、预算、Token/费用和脱敏审计；
- Identity tenant/user/membership、安全 Directory、账号禁用、成员撤销和事务 access-event outbox；
- 独立 Authorization Server、JDBC client/authorization/consent、外部 RSA key、Client Credentials 和 JWT tenant/audience claims；
- 人员/服务 JWT `actor_type` 隔离、官方 OAuth2 Client Credentials Token 获取与缓存；
- 默认关闭的跨运行时 Directory HTTP adapter/client、tenant-bound/平台 scope 与失败关闭邀请校验；
- PostgreSQL outbox lease、重试/耗尽、HTTP relay、Workspace receipt 幂等消费者与 `REVOKED` membership；
- 撤销积压、发布失败、重试耗尽、重复消费与实际撤销数指标；
- Identity 耗尽事件查询、短时双人重放申请、tenant/服务身份隔离和操作审计；
- 无 ACTIVE OWNER 的 Workspace 双人恢复流程，恢复后原 REVOKED OWNER 保持撤销；
- Workspace 授权审计热保留/同库归档、热冷统一查询、稳定游标 SIEM 拉取与导出审计；
- 撤销首次成功传播 Timer/SLO bucket，以及 OWNER 缺失、拒绝窗口、热/归档数和归档失败指标；
- Resource Server 高风险路径/方法选择性 RFC 7662 在线校验，无 active 正向缓存，inactive 401、依赖失败 503；
- Authorization Server 专用 introspection client 隔离、RFC 7009 撤销、官方 JDBC authorization 包装与协议级普通 client 拒绝；
- Identity 当前状态与最新 access-event 组成的人员 Token revocation epoch，以及在线校验放行/拒绝/失败/延时指标；
- 两个发行物的 Prometheus registry 与 exporter、tenantless SERVICE + `platform.metrics.read` 授权，以及独立一分钟 metrics client bootstrap；
- 默认关闭的 tenant 服务 Client 控制面：一次性服务端随机 secret、scope 白名单、tenantless
  operator 双重授权、蓝绿新 ID 轮换、显式退役和同事务操作审计；
- 退役感知 registered client/authorization 包装：阻止新 Token、历史 Token introspection
  inactive，同时保留官方 JDBC authorization 历史可读性；
- 独立的一分钟 client-control operator bootstrap，以及配置失败关闭和真实 PostgreSQL 生命周期
  门禁；
- 测试专用 public client 的 Authorization Code + PKCE S256 真实浏览器会话门禁：登录、
  authorization code 单次交换、错误 verifier、缺失/`plain` challenge 和非法 redirect URI 拒绝；
- JDBC authorization 的 Ainer 人员 principal 精确 Jackson 白名单、认证后凭证擦除、协议记录
  password 排除，以及 public client 无 refresh token 基线；
- 默认关闭的 Spring Security Passkey/WebAuthn、严格 RP/Origin/UV 配置、按账号条件 MFA、
  JDBC 协议与 ACTIVE/REVOKED 生命周期、软撤销、并发最后凭证保护和操作审计；
- 人员 Token 的标准 `amr` / `auth_time` 基线，以及 browser chain 精确 factor accumulation，
  不改变 Client Credentials、internal API 和 metrics 安全链；
- Passkey 真实签名 ceremony 端到端门禁：虚拟 authenticator 驱动 registration/authentication
  签名闭环，Passkey 用户走授权码流程后 access token 携带 `amr=pwd,mfa,pop`、`auth_time`、
  稳定 `sub`/`tenant_id`/`roles`；
- 凭证管理端点（`/webauthn/register/**`）真正受条件 MFA 门禁保护，已登记账号在缺因子时
  无法登记或删除凭证；
- 默认关闭的 Passkey 恢复：自助恢复码（首次登记后签发，高熵一次性、bcrypt 哈希、per-subject
  失败锁定），赎回即吊销该账号全部 ACTIVE Passkey 并写安全操作审计；管理员双人恢复复刻
  Workspace owner recovery 的 request/approve 骨架，approve 吊销目标全部 ACTIVE Passkey；
- 默认关闭的登录限速（node-local 固定窗口，按客户端 IP 节流 `/login`、`/login/webauthn`，
  超额 429）与默认 `optional` 的受控首次 Passkey enrollment（`require-invite` 模式下首登需操作员
  预授权，成功后授权置 CONSUMED，replacement 不受影响）；
- 默认关闭的 resource server step-up 授权策略（`RecentStrongAuthenticationFilter`：高风险路径
  要求人员 Token 的 `amr` 含强因子且 `auth_time` 在 `max-auth-age` 内，否则 403
  `AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED`），首次让 Ainer 签发的 `amr`/`auth_time`
  真正参与授权决策；
- 恢复/enrollment 目标使用 ACTIVE default Identity membership 应用门禁与复合外键双重 tenant 绑定；
  登录限流补齐 WebAuthn options、统一 429/no-store/指标，step-up 补齐匿名 401、USER 限定与
  有界 clock skew；
- Identity 权威运行时提供 tenant 成员列表、加入、角色变更和软移除 API，使用 USER scope + 可信
  tenant claim + 实时 ACTIVE OWNER/ADMIN 四重门禁，所有实际写入同事务审计且不允许通用接口修改 OWNER；
- 首个平台 tenant/OWNER 使用默认关闭、严格幂等、不覆盖密码且由 PostgreSQL transaction advisory
  lock 串行化的 Authorization Server bootstrap；业务 Server 不装配 Identity migration；
- Ainer Admin `dev` public client、双用户 fixture、当前 access token 自助撤销、撤销端点
  active gate、`ainer-admin-v1.yaml` 与 TypeScript SDK 生成入口；TenantMembers 契约与
  tenant selector 已随 S8 删除，契约只保留 `POST /api/me/access-token-revocations`；
- 同一 `ainer-admin-dev` browser session 的 PKCE → revoke → OIDC logout
  真实 PostgreSQL 端到端门禁；成员列表/添加/改角色/软移除验证已随 S8 移除；
- Ainer 品牌服务端登录页：Studio 合同与 Tokens 固定哈希、四种服务端状态、服务端 CSRF、
  SavedRequest、统一凭据错误、HTML 429/`Retry-After`、明确认证基础设施异常 503、精确 CSS
  代理，以及不改变 WebAuthn/MFA filter 的兼容基线；
- 默认关闭的平台 Identity 预配申请控制面：tenantless SERVICE、tenant/user 成对 read/write
  scope、精确 operator 白名单、独立一分钟 operator bootstrap、operator 级幂等、规范化摘要、
  tenant code/新 username 并发预留、惰性过期、同事务平台审计和安全状态查询；
- 平台预配与首租户 bootstrap 共享 tenant code/username advisory lock；申请只预生成
  PostgreSQL UUIDv7 tenant/subject，不提前写核心 tenant/user/membership；
- 新用户短时限次 activation grant（256-bit secret、数据库只存 SHA-256 摘要）、带 key version
  的 AES-256-GCM notification outbox、失败重试与 key rotation 读取；平台投影/审计不返回 secret、
  联系地址、密文或请求摘要；
- 新用户消费 grant 时本人设置首个长期密码；已有 ACTIVE 用户必须用本人 USER Token 与
  `identity.provisioning.accept` 接受。成功事务原子创建 ACTIVE tenant、user（若需要）与 OWNER；
  失败计数锁定、过期、回放和核心写入失败均不产生孤儿 ACTIVE tenant；
- 平台 tenant/user 核心事实安全分页分别受对应 read scope 保护，单页最多 100，不返回密码、
  OAuth、membership、通知或 activation 数据；未激活申请通过显式 cancellation 子资源幂等关闭，
  request、预期 grant、未发布 payload 销毁与阶段审计同事务；
- 默认关闭的预配通知终态回执 API：独立 tenantless gateway client、专用
  `identity.provisioning-notifications.receipts.write` scope、精确白名单、只允许已
  `PUBLISHED` notification、gateway event/notification 双重幂等、UUIDv7 回执和最小安全字段；
- `ainer-dev.xiaoqu99.com` 手工触发的可复现 dev 发布与真实公网环境：独立 PostgreSQL 18.3、
  loopback Authorization Server systemd、版本化 JAR/Studio/Admin、原子切换/校验回滚、
  Let's Encrypt 和精确同源 Nginx 配置；真实 Chromium 已完成 PKCE、成员治理、revoke、
  OIDC logout 和退出后重新登录门禁；
- 通用混合细粒度授权以 ADR-0037 Accepted 为当前基线，ADR-0030 已 Superseded：
  `ainer-module-authorization` 拥有 Spring-free 决策契约、6 张表 PostgreSQL 持久化与实时
  `BindingResolver`、管理 REST API、防提权策略、类型化集合查询、参数绑定产品夹具、决策审计及
  撤权后同 Token 写拒绝。Servlet Web 宿主还会装配 `AinerRequestAuthorizationManager` 与 MVC
  interceptor，真实 JWT HTTP 门禁已证明 ALLOW/403/401 以及拒绝时 controller effect 不发生。
  首版注解只支持 `resourceType=request` 的空-obligation 粗门禁；真实资源 target、字段投影、方法级
  AOP 与生产授权失效 SLA 仍未交付，外部不可变制品门禁仍未关闭。
- ADR-0001 至 ADR-0011、ADR-0015 至 ADR-0020、ADR-0022、ADR-0024 至 ADR-0028、
  ADR-0033 Greenfield、ADR-0035、ADR-0036 与 ADR-0037 已接受（0033 Greenfield 为目标基线，
  Option B：完全移除 Tenant；按
  [Impact](architecture/ainer-foundation-greenfield-reset-impact.md) Stage 0–8 执行，接受不授权
  立即改代码）；ADR-0030 已被 ADR-0037 取代；ADR-0012 至 ADR-0014、ADR-0021、ADR-0023、
  ADR-0029、ADR-0031、ADR-0032 与 ADR-0034 处于 Proposed；
  ADR-0033 v1/v2 标记 Historical；架构、HTTP API、安全、数据、测试、运行与发布基础文档已建立。
- Greenfield S1.2 加法脊柱在 `reset/0033-greenfield` 分支成型且已验证（principal/token-profile/Identity 领域+
  服务+PostgreSQL 持久化+resolver 参考实现，共 identity 74 + security 26 tests / 0 fail），与 legacy 共存、
  未接 runtime；破坏性 cutover 待执行，有序施工清单见 [`0033-greenfield-cutover-plan.md`](architecture/0033-greenfield-cutover-plan.md)。
- Greenfield S2 foundation 能力补全（执行规划 缺口 A）已在 `reset/0033-greenfield` 分支完成：新建
  `ainer_identity_credential`（PASSWORD/WEBAUTHN_PUBLIC_KEY/OIDC_SUBJECT，ACTIVE/REVOKED，部分唯一索引
  `(account_id, type) WHERE status='ACTIVE'`）与 `ainer_identity_human_profile`（0:1 account）两张表；
  `IdentityFoundationService` 扩展 `registerHumanAccountWithPassword` / `findPasswordCredentialForLogin` /
  `rotatePassword` / `updateProfile`，密码经 Delegating PasswordEncoder 编码后入库、rotatedAt 标记轮换、
  未知账号/缺 ACTIVE 凭据 fail-closed；identity 错误码补 CREDENTIAL_NOT_FOUND/CREDENTIAL_REVOKED/
  INVALID_CREDENTIAL/PROFILE_NOT_FOUND。全 reactor 388 tests / 0 failure / 0 error / 0 skipped（Colima）。
   施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S6 canonical Workspace 去 tenant 已在 `reset/0033-greenfield` 分支完成：当前
  `Workspace`/`WorkspaceMember`/审计/recovery 使用 `workspace_id + ACTIVE membership` 边界，
  业务 API 使用 S5 typed `AuthenticatedPrincipal` 并拒绝 Service principal 进入 Human membership；
  MyBatis 查询与 owner 唯一约束已去 tenant，新增 `V202608070100` 从空库重放后删除 Workspace
  schema 的 tenant 列。Identity Directory 改为 ACTIVE HumanAccount 查询，subject-only access event
  只记录 receipt、不跨所有 Workspace 全局撤销 membership；owner recovery、审计导出改为 Workspace
  scope。旧 tenant-first Workspace 测试已重写为 canonical membership/跨 Workspace DENY 门禁。
  全 reactor 387 tests / 0 failure / 0 error / 0 skipped（Colima）。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S7 AI Runtime 去 tenant 已在 `reset/0033-greenfield` 分支完成：
  `GovernedAiExecutionContext`、Invocation/Task/ContextSnapshot、AI audit/budget repository 与 API
  改用 typed `AuthenticatedPrincipal` 的 subject/actor context；node-local limiter 更名为
  `SubjectRateLimiter`，PostgreSQL daily budget/advisory lock 按 subject 绑定，模型调用、Task run、
  SSE、审计读取与 cross-subject 404 回归保持通过。新增 AI migration 移除 invocation/task/snapshot 的
  tenant 列，配置改为 `subject-daily-budget`。全 reactor 387 tests / 0 failure / 0 error / 0 skipped
  （Colima）。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S8 原子删除 legacy + migration squash 已在 `reset/0033-greenfield` 分支完成（不可逆点）：
  删除 legacy identity `account/` 领域/应用/基础设施约 50 文件、AuthServer legacy controllers 约 20
  文件、`tenantcontext/` 与 tenant selection、`RevocationAwareOAuth2AuthorizationService` 的 legacy
  tenant/user 在线状态依赖、legacy `AuthenticatedActor` 体系（被 typed `AuthenticatedPrincipal` 取代）、
  `AinerResourceServerProperties.tenantClaim`/`AuthenticatedService` 的 tenantId，以及 legacy workspace
  identity access-event 消费链路（subject-only 语义已由 canonical Workspace 覆盖）。`foundation/` 包
  提升为 identity 主体包（`IdentityErrorCode` 收口至 foundation）。migration 重建为 4 个可空库重放的
  standalone baseline：identity=`V202608070300`、workspace=`V202608070310`、ai-runtime=`V202608070320`、
  authorization-server=`V202608070330`，旧库不可原地升级（ADR-0033 前提）。全仓 `tenant_id`/`tenantId`
  grep 仅剩测试内"列不存在"负向断言与参数命名。全 reactor 205 tests / 0 failure / 0 error / 0 skipped
  （Colima），0 skipped。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
  `AinerUserDetails` 加性重设计（新增 nullable `accountId` + `securityEpoch`，legacy 字段保留），
  customizer 抽为可测的 `AinerJwtTokenCustomizer`：client setting `ainer.token-profile` 选择轨道，
  SERVICE_V1（ServicePrincipal sub + token_profile/claim_contract_version=1/actor_type=SERVICE +
  sec_epoch，无 tenant_id）与 USER_NEUTRAL_V1（HumanAccount sub + profile claims + sec_epoch，
  保留 amr/auth_time，无 tenant_id/roles）均 fail-closed（缺 principal/account、非 ACTIVE、未知
  profile → OAuth2 400 access_denied，不回退 legacy）；无 setting 的 client 走原 legacy claims 零回归。
  常量 `TOKEN_PROFILE_SETTING`/`SEC_EPOCH_CLAIM` 收口于配置类；JSON mixin 同步 accountId/securityEpoch
   往返。全 reactor 401 tests / 0 failure / 0 error / 0 skipped（Colima）。
   施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S4 登录链路与 Passkey foundation 接线已在 `reset/0033-greenfield` 分支完成：
  `AinerUserDetailsService` foundation-first 读取 `HumanAccount + PASSWORD credential`，旧 tenant
  账号保留 fallback/legacy context enrichment；foundation-only 账号可在无 Workspace 下完成密码+PKCE，
  token 使用 `USER_NEUTRAL_V1`。平台 bootstrap 改为创建 foundation account/profile，dev fixture 在共存期
  同时保留 legacy tenant 投影；Passkey credential、recovery code、enrollment grant、双人 recovery
  增加 `account_id` 绑定与 account 控制面 API，legacy `(tenant_id, subject_id)` API 保留。新增两条
  PostgreSQL migration，Authorization Server 从 23 增至 25 migrations；真实密码、WebAuthn ceremony、
  account recovery/enrollment/admin recovery 均通过。全 reactor 407 tests / 0 failure / 0 error / 0 skipped
  （Colima）。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- Greenfield S5 Resource Server typed profile resolver 已在 `reset/0033-greenfield` 分支完成：新增
  `AuthenticatedPrincipalResolver` core port、Spring `Jwt` 到 `VerifiedJwtClaims` adapter，以及 starter
  的 SecurityContext resolver；`USER_NEUTRAL_V1`/`SERVICE_V1` 分别解析为 Human/Service subject，
  `sec_epoch` 解析为 optional typed epoch，SERVICE 无 `amr` 时使用 `client_credentials` assurance。
  缺失、未知、版本不支持或 actor/profile 矛盾统一 fail-closed 为 401；legacy
  `AuthenticatedActorResolver` 保持独立零回归。全 reactor 411 tests / 0 failure / 0 error / 0 skipped
  （Colima）。
  施工序列与决策表更新见 [`0033-greenfield-atomic-cutover-execution-plan.md`](architecture/0033-greenfield-atomic-cutover-execution-plan.md)。
- M4.8B 租户上下文选择代码基线：`GET /api/me/tenants` 返回当前 USER 的 ACTIVE membership
  安全投影（tenant ID/code/name/role/is_default），LOCKED/DISABLED tenant/user/membership
  不返回；`AinerTenantSelectionFilter` 在 Authorization Code + PKCE 流程的 authorization
  endpoint 拦截多 ACTIVE membership 人员并重定向到服务端渲染的 `/select-tenant` 选择页，
  选择结果绑定当前 AS 会话（session attribute）与 authorization request（principal 更新后
  持久化进 `OAuth2Authorization`）；token customizer 在签发人员 access token 前实时重查
  `findActiveMembership(tenantId, subjectId)` 校验关系仍然 ACTIVE 并取得当前角色，principal
  或客户端提交的 tenant 只作为候选；SERVICE token 被该端点 403 拒绝。
- M4.8C OWNER 专用转移代码基线：双自然人确认状态机（REQUESTED → EXECUTED / CANCELLED /
  EXPIRED），`OwnershipTransferService` 在单一事务中锁定双方 membership、再次校验 ACTIVE 角色、
  先降原 OWNER 为 ADMIN 再升目标 ADMIN 为 OWNER、写入 `OWNERSHIP_TRANSFERRED` 操作审计并为
  双方写入 `IDENTITY_MEMBERSHIP_ROLE_CHANGED` access event 使旧角色 Token 进入撤销链路；
  数据库部分唯一索引保证每 tenant 最多一个 ACTIVE OWNER 和一个未完成转移；
  `OwnershipTransferController` 暴露 initiate/get/accept/cancel 四个端点，要求
  `tenant.ownership.transfer` scope、可信 tenant claim 与实时角色门禁。
- M4.8C OWNER 丢失恢复代码基线：双 tenantless SERVICE request/approve（不同 service subject），
  只能提升现有 ACTIVE ADMIN 为 OWNER 并降原 OWNER 为 ADMIN，不恢复被禁用主体；独立表/端点
  （`/internal/identity/ownership-recovery/**`）与 scope（`identity.ownership-recovery.request|approve`），
  与正常转移不共用授权规则；security operation audit 记录 REQUESTED + EXECUTED 两阶段。
- ownership-transfer step-up 门禁：默认关闭，启用后要求人员 Token 的 `amr` 含强因子且
  `auth_time` 在 `maxAuthAge` 内才能执行所有权转移。

## 3. 最近验证记录

2026-08-18 `v1.0.0` 已发布——**1.0 产品合同定稿，G0–G4 全部关闭（1.0 达成）**
- **发布**：准备 PR #18（ADR-0040 验收记录 + ADR-0046 LTS 条款 + CHANGELOG/README）合入
  `622b249`（dev CI 14m30s 绿）→ annotated tag `v1.0.0` → release run `32155505204` 全绿
  （26 projects / 122/122 主制品读回验签、空仓消费者、Initializer 三通道、SBOM/provenance、
  16 个签名证据资产）。读回：`immutable=true`、非 prerelease、精确绑定 merge commit。
  自 `0.2.0` 起零代码差异——合同声明发布，ADR-0040 的 1.x 兼容承诺正式生效；`1.0.x` 为
  首个 LTS 线（ADR-0045/0046）。
- **双消费者 1.0.0 矩阵**：`xq-platform-next` `0.2.0 → 1.0.0`（14/0/0/0）+ 回滚
  `1.0.0 → 0.2.0`（14/0/0/0）后固定 `1.0.0`——**完整升级链 `rc.2 → rc.3 → 0.1.0 →
  0.2.0 → 1.0.0`，每级回滚终点均验证**；`python-learning-service` `0.2.0 → 1.0.0`
  （8/0/0/0，隔离冷仓）。两消费者均提交入各自 git 历史。
- **许可边界**：1.0.0 是工程合同定稿，公开发行/开源仍需 LICENSE/NOTICE、品牌资产与对外
  许可决策（ADR-0044/0040 边界继续适用）；Scaffold Ready 后的产品化阶段按
  `design/ainer-scaffold-design.md` 路线继续。


2026-08-18 `v0.2.0` 已发布 + 双消费者升级/回滚矩阵（ADR-0040 G4 核心证据闭环）
- **发布**：准备 PR #16 合入默认分支（`62829dc`，dev CI 12m50s 绿）→ annotated tag
  `v0.2.0` → release run `32123318369` 全绿（26 projects / **122/122 主制品读回验签**、
  空仓消费者、Initializer 三通道、SBOM/provenance、16 个签名证据资产）。读回核验：
  `immutable=true`、非 draft 非 prerelease、精确绑定 merge commit。G3 四切片全部加性变更
  （旧构造器保留、default 方法、migration 只追加）——**ADR-0040 兼容承诺的首次真实验证**。
- **双消费者矩阵（G4 门禁 §2.3）**：`xq-platform-next` `0.1.0 → 0.2.0`（14/0/0/0：JWT 链、
  撤销传播、migration replay、HTTP 错误、SDK 门禁）+ `0.2.0 → 0.1.0` 回滚验证（14/0/0/0）
  后固定 `0.2.0`——累计升级链 `rc.2 → rc.3 → 0.1.0 → 0.2.0`；`python-learning-service`
  `0.1.0 → 0.2.0`（8/0/0/0，隔离冷仓全部 0.2.0 制品远端解析）。两消费者业务域与测试面
  完全独立。回滚终点与升级路径均有 git 提交证据。
- **ADR-0045 Accepted**：版本策略（0.x Stable 层只加不破 / 1.x 全量承诺）、相邻 minor
  升级 + 一级回滚窗口、patch 规则（零 API/配置/schema 变化）、0.x 不设正式 LTS（1.0 前
  另立 ADR 定稿）、兼容检查落地形态（双消费者全绿 = HTTP/Java 真实证据、migration 重放 =
  schema 证据、配置元数据契约 = config 证据）。本次矩阵为其首批验收证据。
- **G4 剩余**：评估 1.0.0 发布（G0–G4 门禁逐项核对：双消费者 ✅、连续升级回滚 ✅、兼容
  检查 ✅（ADR-0045 §4）、LTS/补丁策略 ✅）。


2026-08-17 G4 开篇：第二参考消费者 `python-learning-service` 接入
- **定位**：`python-interactive-learning`（Angular/Pyodide 前端）的平台后端；Ainer 第二个
  独立参考消费者（scaffold-design §13.5 登记，Java 制品消费，拒绝源码副本）。
- **交付**：Initializer（本地 CLI jar）按 manifest v1 生成独立仓库（postgres 变体 +
  learningEvidence 实体 + 自有 Maven Wrapper）；接入 GitHub Packages（env 注入认证）；**隔离
  冷仓基线全部 0.1.0 制品远端解析，4/0/0/0**；Evidence 云端存档首切片——JWT 安全链
  （learner 从 sub 绑定）、跨学员统一 404 不泄露存在性、append-only（无 update/delete）、
  `learning.evidence.read/write` scope、`LEARNING.EVIDENCE.*` 错误码、V2 learner 归属列。
- **验证**：隔离冷仓 8 tests / 0 failure / 0 error / 0 skipped（真实 PostgreSQL 18.3
  Testcontainers；401/403/201/跨学员 404/422 矩阵）。至此双消费者并存：xq-platform-next
  （0.1.0 升级链已闭环）+ python-learning-service（0.1.0 冷仓接入）。
- **G4 剩余**：双消费者连续升级/回滚矩阵、HTTP/Java/schema/config 兼容检查、LTS/补丁
  策略成文；Tutor（经 AI Runtime）属产品后续切片。


2026-08-15 G3 第四切片：Knowledge Foundation K1/K2（ADR-0044，**G3 产品核心闭环整体完成**）
- **范围**：ADR-0034 目标合同的首批实现切片。K1 身份与版本（KnowledgeObject 语义身份 +
  不可变 Revision + SUPERSEDES lineage + asOf 精确 pin）；K2 信任与生命周期（SourceRef/
  EvidenceLink + append-only 生命周期 + **人工发布门禁**：SERVICE 提案允许、发布一律 403）。
- **交付**：`ainer-module-knowledge`（第 26 个 reactor 模块，发布链 26 projects/122 primary），
  `/api/knowledge/**`（objects/revisions/publications 动作名词端点），`knowledge.read/manage`
  scope 应用服务内强制，`AINER.KNOWLEDGE.*` 错误码；时间入口微秒截断。
- **验证**：模块真 JWT HTTP 5/0/0/0（0 skipped）；全量 reactor 数字见最新记录。
- **边界**：向量/图索引、Context Assembly、OKF import/export、PlatformCatalog 属
  ADR-0034 Phase 2–4，未交付不得宣称。

2026-08-15 G3 第三切片：Agent 代行 A1（ADR-0043，取代 ADR-0031）
- **范围**：一层 principal→agent 委托 + 委托检查点。ADR-0031 以 Greenfield 语义合规取代
  （Workspace 锚点、SubjectRef 与 ADR-0037 对齐、撤销语义复用决策时实时解析）。
- **交付**：授权侧 ActingGrant（permission 子集子表 + 单一结构化 Scope；签发强制
  agentDelegable ∧ principal live effective 子集 ∧ scope 被覆盖 ∧ GLOBAL/system-only 拒绝；
  `check` 检查点拉取式实时解析 grant/principal bindings/agent 状态）；ai-runtime 侧独立
  `AiAgentModuleConfiguration` + Agent 定义注册表 + `/api/ai/agents`；默认状态解析 fail-closed。
- **验证**：ai-runtime 28/0/0/0（新增委托端到端 4 项：ALLOW、退役即拒、权限收缩即拒、
  撤委托即拒、签发矩阵 422×2）。全量数字见最新 reactor 记录。
- **边界**：A2（Capability catalog/Context 授权）、A3（Tool/副作用/Challenge 检查点）、
  A4（Token Exchange）未交付；`Permission.agentDelegable` 自本切片真实消费。

2026-08-14 G3 第二切片：SubjectSet 授权集成（ADR-0042 O2，撤岗即失权闭环）
- **范围**：ADR-0037 加性扩展 + 组织模块首个集合族。岗位在岗者通过集合绑定获得授权，
  组织事实变化（暂停/终止/撤岗）经决策时实时解析在下次决策立即生效——无事件、无缓存。
- **交付**：决策引擎集合授予路径（旧构造器源兼容，缺省 fail-closed）；
  `ainer_authorization_subject_set_binding` 加性 migration；管理 API `/api/authorization/set-bindings/**`；
  创建防提权矩阵（GLOBAL/system-only/HIGH/一致性/未知族/自成员）；
  `workforce.position#assignee` 成员解析器（在岗 join 查询，validUntil 父链最早）。
- **验证**：授权模块 77/0/0/0（新增 7）+ 组织模块 15/0/0/0（新增撤岗即失权端到端 1）；
  全量 reactor 数字见下方 G3 首切片之后的最新记录。
- **边界**：Decision validUntil 传递与决策审计的集合 provenance 明细属后续增强；O3
  （Team/Leadership/ReportingLine/子树/SCIM）按需追加。

2026-08-14 G3 首切片：组织目录模块 `ainer-module-organization`（ADR-0042 O1）
- **范围**：ADR-0032 组织目录基线以 Greenfield 模型合规取代——ADR-0042 Accepted（Workspace
  锚点取代 Tenant；撤销语义从 access-event outbox 矩阵改为决策时实时解析；SubjectRef 与
  ADR-0037 对齐）。O1 切片交付第 25 个 reactor 模块。
- **交付**：`V202608140400` migration（8 张表 + append-only 审计；`btree_gist` +
  `tstzrange` EXCLUDE 强制同目录同 Subject 非 REVOKED 任职期不重叠；`(workspace_id,
  directory_id, id)` 复合 FK 阻止跨目录引用；position_assignment 以 5 列复合 FK 锚定同
  Engagement 同 Unit 的 UnitAssignment；开放 PRIMARY/ROOT 部分唯一索引）。命令式管理 API
  `/api/organization/**`（create/transfer/suspend/terminate 动作名词端点、分页 ≤100、
  `organization.read/manage` scope 应用服务内强制、`AINER.ORGANIZATION.*` 错误码）；成员/
  岗位投影按评估时间实时解析父链（无事实缓存，暂停/终止下一次查询即生效）；trusted-issuer
  未配置时 fail-closed 拒绝创建任职。装配进 ainer-server 与发布链（25 projects）。
- **验证**：服务层 8 项 + 真 JWT HTTP 6 项（JwtTestSupport）= 14 tests / 0 failure /
  0 error / 0 skipped（真实 PostgreSQL 18.3 Testcontainers）。
- **边界**：O2（SubjectSetBinding + position assignee resolver + ADR-0037 集成 + 防提权
  矩阵）未交付，不得宣称组织派生授权；Team/UnitLeadership/ReportingLine/SCIM 未实现。

2026-08-14 G2 消费者验证：xq-platform-next 远端消费 + 升级/回滚 + 产品纵向切片
- **远端消费**：消费者 pom 固定 `dev.ainer:ainer-dependencies:0.1.0-rc.2/3`，新增
  GitHub Packages `<repositories>` 与 `.mvn/github-packages-settings.xml`（`${env.GITHUB_PACKAGES_USER/TOKEN}`
  注入，模板零密钥）。冷仓基线使用隔离 `maven.repo.local=/tmp/xq-cold-repo-rc2`，rc.2/rc.3
  制品全部从 `maven.pkg.github.com/ainerlab/ainer-boot` 远端解析（本机 `gh` 凭据具备
  `read:packages`），4 tests / 0 failure / 0 error / 0 skipped，BUILD SUCCESS。
- **升级/回滚演练**：`rc.2 冷仓基线 → rc.3 升级 → rc.2 回滚 → 最终固定 rc.3`，每步全量
  verify 均 4/0/0/0；消费者 git 历史（38b2f72/c355108/440cb07/60c349c）保留完整演练序列，
  rc.2 证明为有效回滚终点。
- **产品纵向切片**（提交 `fbb1c2a`）：消费者自有 RSA 真签名 JWT 夹具（USER_NEUTRAL_V1/
  SERVICE_V1 + NimbusJwtDecoder 验签，无 stub）驱动——JWT 安全链（匿名 401、缺 scope 403、
  `XQ.PLATFORM.APP_NOT_FOUND` 404、重复 appCode 409、缺 workspace 422、X-Request-Id）；
  Ainer 授权模块消费（产品权限 `xq.platform-app.read/write` 注册、scope 天花板、
  BINDING_REQUIRED 策略、`xq-ops` 管理白名单；ops SERVICE 经管理 API 建 Role/Binding，
  业务 USER 受保护写 ALLOW；**撤销 Binding 后同一串 JWT 立即 403 且产品行不变**，跨
  Workspace 拒绝，白名单外 SERVICE 管理被拒；ALLOW/DENY 决策审计断言）；V2 migration
  （platform app 增加 `workspace_id`，空库重放 V1+V2+授权基线）；OpenAPI 契约
  `xq-platform-v1.yaml` + `scripts/verify-sdk.sh`（strict 校验 + typescript-fetch 生成 +
  tsc 5.9.2 编译门禁）。
- **消费者验证**：14 tests / 0 failure / 0 error / 0 skipped（真实 PostgreSQL 18.3
  Testcontainers）；SDK 门禁通过。过程中暴露并修复的消费者侧事实：模块自带显式 `@MapperScan`
  会使 MyBatis 自动扫描退避（宿主必须显式登记自己的 mapper）；`PermissionCode` 仅接受
  全小写；`@Value` 不能绑定 YAML 列表；权限目录表是管理投影，宿主需启动时 upsert 同步。
- **边界**：消费者仓库无远端 CI（本地证据）；weapp 前端工程尚未创建，SDK 的 tsc 编译门禁
  是「可被前端工程编译」的当前最强形式；`0.1.0` 发布后消费者需再执行一次
  `rc.3 → 0.1.0` 升级验证。

2026-08-14 P3 三模块服务端管理 API（G1 管理 API 项关闭，G1 整体关闭）
- **范围**：ADR-0040 G1 退出条件「管理 API 补齐」。此前 dictionary/config/notification 只有
  domain/application/infrastructure，零 HTTP 面、零稳定错误码、零 scope、零审计。
- **交付**：三模块 `*ErrorCode` enum（含 CONCURRENT_MODIFICATION 409）+ `*Authorities` scope +
  管理 Controller/DTO（乐观锁部分更新、`status-changes` 动作名词端点、分页 ≤100）+
  `IllegalArgumentException → BusinessException` 全量迁移；dictionary/notification 新增 append-only
  审计表与同事务写入，config 复用 ConfigHistory；写入面补齐（字典类型/项更新与启停、通知模板
  更新/启停/分页、投递记录状态分页且不回显渲染内容）；`ainer-test-support` 新增 `JwtTestSupport`
  （RSA 3072 签发 USER_NEUTRAL_V1/SERVICE_V1、真 NimbusJwtDecoder、生产等价 @Primary resolver 工厂）。
- **验证**：三模块定向（dictionary 17、config 14、notification 12）后全量 `./mvnw clean verify`
  （JDK 25 + Colima）：**374 tests / 0 failure / 0 error / 0 skipped**，24 模块全部 SUCCESS；
  `git diff --check` 通过。真 JWT HTTP 覆盖 401 无 token、403 缺 scope、201/200 生命周期、
  409 并发与重复、审计行 DB 断言、secret/PII 脱敏。
- **边界**：OpenAPI 运行时文档未引入（Boot 4.1 springdoc 兼容性未验证，引入需依赖台账评估，
  不阻塞 G1 关闭）；`NotificationDeliveryEngine` 的 `@EnableScheduling` 仍依赖 ainer-server 侧
  配置类，属既有已知项。

2026-08-14 文件存储模块 `ainer-module-file`（G1 文件元数据补齐）
- **范围**：ADR-0040 规格「上传/下载/删除、元数据、大小/类型限制、路径遍历防护」。此前
  `FileStoragePort` SPI 与本地适配器已存在但零消费、零元数据持久化、零限制检查。
- **交付**：新模块含 `V202608140100__file_baseline.sql`（`ainer_file_object` UUIDv7 CHECK +
  `ainer_file_audit` append-only，file_id FK ON DELETE SET NULL）；application/infrastructure/api
  全层；`FileStorageApplicationService` 提供 upload（DigestInputStream 边存边算 SHA-256、存后
  核实实际大小、DB 失败删除已存字节补偿）/download/delete（审计先插、元数据同事务删、字节后删，
  孤儿容忍）/page（size≤100）；`FileStorageController` `/api/files`（multipart 201、流式下载
  Content-Disposition、DELETE）；`AINER.FILE.*` 错误码（413/415 真实语义）；`file.read`/`file.write`
  scope 手动强制。
- **发布链**：BOM 注册、root modules、ainer-server 装配、`release-artifacts.txt` 24 projects、
  `check-release-contracts.sh`/`verify-remote-release-artifacts.sh` 24/112、
  `verify-maven-consumers.sh` 24 POM；off-state 冒烟测试补 `ainer.file.enabled=false`。
- **验证**：服务层 11 项（元数据/字节落盘、SHA-256、超限补偿清理、类型拒绝、scope 403、下载
  roundtrip、删除后审计保留、孤儿清理、分页过滤、id v7）+ 真签名 JWT HTTP 6 项（401/403/201/
  413/415/审计行）。全量 `./mvnw clean verify`（JDK 25 + Colima）：**355 tests / 0 failure /
  0 error / 0 skipped**，24 模块全部 SUCCESS；`git diff --check` 通过。
- **边界**：OpenAPI 运行时文档未引入（Boot 4.1 下 springdoc 兼容性未验证，与 P3 管理 API 批次
  统一决策）；S3/OSS 适配器、病毒扫描/魔数嗅探不在 ADR-0040 规格；`workspace_id` 归属列为可空，
  产品接入时绑定。

2026-08-13 持久化身份全域 UUIDv7（G1 硬化收口）
- **范围**：ADR-0040 Stable 契约与 G1 退出条件要求「零 `UUID.randomUUID()` 在持久化路径」。本次把
  Workspace、AI Runtime 与 Authorization Server 三个发行物的持久化主键/审计/恢复 ID 从 UUIDv4 统一
  迁移到应用层 `Uuidv7.generate()`（与 P3 模块既有惯例一致，全限定内联调用）。
- **改动**：20 处持久化路径调用点替换；数据库 schema/migration 零改动（所有 `id` 列已为 `UUID` 或
  `VARCHAR(100/128)`，v7 字符串兼容）；framework 的请求追踪 ID（`RequestIdFilter`/`RequestIds`）
  非持久化身份，保留 `UUID.randomUUID()`。
- **加固**：Workspace 与 AI 持久化集成测试新增 `id().version() == 7` 断言，证明生成的持久化主键
  确为 UUIDv7（匹配 identity/persistence 模块既有断言风格）。
- **验证**：全仓 `src/main/java` grep 确认持久化路径 `UUID.randomUUID()` 残留为 0（仅剩 2 处请求
  追踪与 `Uuidv7` javadoc 文字）。JDK 25 + Colima 的 `./mvnw clean verify` 全绿，**338 tests /
  0 failure / 0 error / 0 skipped**，零回归；`git diff --check` 通过。
- **边界**：统一 `RETURNING`、全域应用层生成与 1.0 clean baseline 的最终收敛仍属后续；本次不改变
  identity 模块的 DB 端 `uuidv7()` 机制，不触及数据库 migration；G1 的文件元数据持久化与 P3 服务端
  管理 API/OpenAPI 仍未完成，G1 未整体关闭。

2026-08-13 `xq-platform-next` 消费者接管审计、Initializer Wrapper 补正与 `rc.3` 发布
- **消费者事实**：独立仓库由 Initializer 生成，当前仍固定 `0.1.0-SNAPSHOT`；README 要求
  `./mvnw`，仓库内却没有 Wrapper。这证明 `rc.2` 的远端 Initializer 三通道只验证了生成源码与
  Ainer 制品消费，没有验证生成项目可独立携带约定工具链。
- **根因**：`ProjectGenerator` 未把 Wrapper 资产加入生成树；`verify-initializer-consumer.sh`、
  TTFR 与 TTCRUD 均用 Ainer 根目录的 Maven 4 Wrapper 执行生成项目，掩盖了缺失资产和执行位。
- **本地修复**：生成树增加 Apache Maven Wrapper 3.3.4 的 Unix/Windows 脚本及固定 Maven
  3.9.16/SHA-256 的配置；POSIX 写入固定普通文件 `0644`、`mvnw` `0755`，`diff` 把执行位漂移
  计为修改；三类门禁改用生成项目自己的 Wrapper，并修复 TTFR/TTCRUD 的已有仓库复用参数。
- **本地证据**：JDK 25 + Maven 4.0.0-rc-6 的 23 模块 `clean verify` 全绿，**338 tests /
  0 failure / 0 error / 0 skipped**。普通、PostgreSQL、CRUD 三种生成项目分别通过 2、1、4 项
  测试，均为零失败/错误/跳过，且全部执行生成项目自己的 Maven 3.9.16 Wrapper；PostgreSQL
  18.3 migration 从空库成功重放。冷仓 TTFR 147s / 600s、TTCRUD 180s / 1800s，发布合同脚本、
  Wrapper 资产比对与 `git diff --check` 通过。
- **远端代码证据**：PR #5 的 run `31672808212` 全绿并合入 `dev`；默认分支 run
  `31673523854` 在精确 merge commit `19151697a508a7e21ed4fa5838b8a384dfe7a582` 上再次通过
  quality、gitleaks、Maven 3/4 consumer、Initializer 三通道、TTFR、TTCRUD、SBOM 与完整
  platform/virtual-thread matrix。发布冻结 PR #6 合入后，最终默认分支 run `31675092195` 又在
  merge commit `666b1556f11935925369586152a3791180b7314e` 上通过同一完整门禁。
- **远端发布证据**：release run `31675920731` 全绿；338/0/0/0，107 个主制品及 107 个 `.asc`
  全部远端读回验签，Maven 3/Maven 4 空仓消费和远端 Initializer 三通道通过。Release API 读回
  `immutable=true`、`prerelease=true`、精确 target commit 与 16 个资产；GitHub Attestations 未启用，
  强制的项目签名 provenance、SBOM 和 SHA-256/SHA-512 清单均已签名上传。
- **独立发布读回**：重新下载 16 个 Release 资产，在隔离 keyring 验证全部适用 detached signatures，
  fingerprint 为 `DC72A6994ABFA48B3D9B1DE145361DCB6F65F6FD`；`EVIDENCE-SHA256SUMS`
  的 14 项全部匹配，release evidence 记录 107 个远端主制品与精确源码 SHA。
- **权限边界**：本机当前 GitHub 凭据没有 `read:packages`，因此不能把 `xq-platform-next` 的
  `0.1.0-SNAPSHOT` 立即替换成远端 RC 并执行冷仓消费。发布修复和通用远端门禁可先推进；真实
  产品消费仍需最小 `read:packages` 授权，不使用本地缓存伪装远端证据。

2026-08-13 `v0.1.0-rc.2` 合格受控 RC 发布
- **源码与发布身份**：PR #3 以 merge commit `0f99ee08f5d9145bc5bc72052eaf59774aad8054`
  合入默认分支；默认分支 run `31666240720` 全绿。annotated tag `v0.1.0-rc.2`、release workflow
  源码和 immutable GitHub Release 均精确指向该 commit；Release 是 prerelease、非 draft、
  `immutable=true`，共 16 个 assets。
- **构建与签名**：release run `31666957663` 全绿，336 tests / 0 failure / 0 error / 0 skipped；正式
  primary fingerprint 为 `DC72A6994ABFA48B3D9B1DE145361DCB6F65F6FD`，passphrase-protected
  signing subkey 的导入、probe、reactor 与 parentless BOM 签名均通过。
- **远端制品与证据**：从 GitHub Packages 完整读回 107 个主制品和 107 个 `.asc`，全部验证为精确
  fingerprint；远端 digest 生成 SHA-256/SHA-512 清单，CycloneDX SBOM 与 107-subject Ainer
  provenance 一并签名后进入 Release。GitHub Attestations 未显式启用，步骤按 ADR-0041 设计跳过，
  不影响强制项目 provenance 门禁。
- **远端消费者**：Maven 3.9.16、Maven 4 和 Project Initializer 均从全新本地仓库消费远端坐标；
  Initializer 普通、PostgreSQL、CRUD 三通道全部通过，没有回退到 reactor install 或既有缓存。
- **独立回读**：发布后重新下载全部 16 个 assets，在隔离 GPG home 导入 Release 公钥并确认 primary
  fingerprint，7 个 detached signatures 全部通过；`EVIDENCE-SHA256SUMS` 的 14 项全部通过，
  provenance 中版本、tag、源码 SHA 和 107 个 subjects 与发布事实一致。
- **结论边界**：P1 合格受控 RC 发布门禁关闭；G2 仍等待 `xq-platform-next` 的真实产品消费、
  migration replay、升级与回滚。该版本不是稳定 `0.1.0`、公开发行、生产就绪或 1.0 声明。

2026-08-13 G2 发布事故复核与 release hardening（发布前实施记录）
- **`rc.1` 结论**：run `31658811613` 的 build/sign/deploy 与 336/0/0/0 成功，不等于合格发布；
  provenance 因私有仓库计费限制失败，后续 tag/workflow 修复导致 tag/source 与已部署字节不一致，
  同版本重跑得到 409。ADR-0041 将其标记为 withdrawn/non-qualifying，禁止消费或复用。
- **签名密钥收紧**：根 reactor 与 parentless BOM 恢复 Maven GPG Plugin `bestPractices=true` 和
  `MAVEN_GPG_PASSPHRASE` 绑定；workflow 拒绝空口令，在 deploy 前执行签名 probe，删除 CLI passphrase。
  导入 key 还必须匹配 repository variable 固定的 40 位 fingerprint。2026-08-13 已轮换为
  passphrase-protected RSA-3072 certification primary + signing subkey；CI 只保存 signing subkey，正式
  fingerprint 为 `DC72A6994ABFA48B3D9B1DE145361DCB6F65F6FD`，Secrets 与 repository variable 已更新。
- **发布前门禁**：只接受与事件源码及默认分支头一致的 annotated SemVer tag；目标 BOM 版本存在即
  失败；GitHub Immutable Releases 已于 2026-08-13 远端启用（API readback `enabled=true`），workflow
  还要求 repository variable 声明并在 Release 创建后读回 `immutable=true`；shell 合同禁止再次硬编码
  `ab` 路径、拼错版本变量或放行 attestation。
- **发布后门禁**：Maven 3/4 分别从空本地仓库消费远端 BOM/Starter，Initializer CLI 也从远端 classifier
  获取；完整读回 23 个 project 的 build/consumer POM、20 组 main/sources/Javadoc 与 CLI classifier，
  共 107 个主制品和 107 个 `.asc`，逐一验证精确 fingerprint。`scripts/release-artifacts.txt` 是唯一
  制品清单，合同测试将它与实际 reactor POM 对照，避免新增模块静默漏验。
- **证据策略**：CycloneDX SBOM、远端 SHA-256/SHA-512、Ainer 签名 provenance、公钥/fingerprint 和
  证据签名是强制 release assets；GitHub Attestations 仅在显式启用且计费支持时作为附加失败关闭门禁，
  不再 `continue-on-error`，也不把项目 provenance 宣称为 GitHub Attestation/SLSA 等级。
- **虚拟线程矩阵**：修复 Ubuntu 上 `command -v ab` 检查后仍硬编码 `/usr/sbin/ab` 的 CI 失败，统一使用
  实际解析路径，并修正 `AINNER_VERSION` 拼写。缩短版本地双模式矩阵通过：平台/虚拟两种模式的
  JDBC 与等待接口各 40 请求。run `31664911800` 的完整矩阵四场景各完成 2000 请求，Non-2xx、
  Connect、Receive、Exceptions 全为 0；Length-only 分别为 `2/0/4/0`。脚本现显式解析结果并对真实
  HTTP/传输失败关闭，不再让 `ab ... || true` 掩盖故障；Length 差异只作为动态响应观测记录。
- **本地验证**：JDK 25 + Maven 4.0.0-rc-6 `./mvnw clean verify` 为 23/23 SUCCESS，
  **336 tests / 0 failure / 0 error / 0 skipped**；本地 Maven 3/4 Golden Consumer、Initializer
  普通/PostgreSQL/CRUD 门禁通过。passphrase-protected 临时 key 已分别验证根 reactor 与 parentless BOM
  的 GPG 配置；本地 HTTP registry 夹具完成 107 个主制品 + 107 个签名读回、精确 fingerprint 验证和
  107-subject provenance。shell/发布合同、严格 SemVer 正负例、版本存在性正负例、workflow YAML 与
  `git diff --check` 均通过。
- **PR 远端验证**：最终代码 commit `1328d27` 的 run `31664911800` 全绿：quality、gitleaks、完整
  virtual-thread matrix 成功；336/0/0/0，Maven 3/4 Consumer 与 Initializer 三通道通过，TTFR 48s、
  TTCRUD 73s。该结果仍是 PR head 证据，合并 commit 必须由默认分支 CI 单独验证。
- **发布前证据边界（历史）**：本地 registry 结果使用一次性夹具，不是 GitHub Packages 证据；
  当时尚未执行 tag workflow。该边界已由上方 `v0.1.0-rc.2` 发布记录关闭，不作为当前状态。

2026-08-12 历史 ADR-0038 批次：P4 范围精简与企业基建前置（已被 ADR-0040 取代）
- **决策状态**：下列方向是当时记录；ADR-0038 后续被直接改写，现已由 ADR-0040 合规取代。当前
  Stable/Incubating/非目标与 G0–G4 路线只以 ADR-0040 为准，尤其“通知/任务/缓存只给端口”的结论
  不再生效。
- **文件存储 SPI**（首批 P3 基建代码）：`FileStoragePort` 端口 + `StoredFile` record +
  `StorageErrorCode`（ainer-core）；`LocalFileStorageAdapter` 本地适配器 +
  `LocalFileStorageAutoConfiguration`（ainer-spring，`@ConditionalOnMissingBean`，产品可覆盖）；
  UUID 存储键、namespace 隔离、路径遍历防护；10 项单元测试全绿。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：**309 tests / 0 failure / 0 error /
  0 skipped**。零回归。
- **下一批**：字典/配置极简模块（`type-code-label` + `namespace-key-value`）。

2026-08-11 P0 治理补齐：CODEOWNERS + 分支保护状态确认
- 新增 `.github/CODEOWNERS`：按安全/身份、授权、数据库、AI、Workspace、构建发布、Initializer、
  文档决策等领域定义默认 reviewer（`@codefitx`）。当前 private + GitHub 免费版分支保护受计费限制
  （HTTP 403），CODEOWNERS 为 PR review 默认值与未来多协作者领域归属服务；仓库可见性或计费变更后
  在 GitHub 设置启用 required review 即可生效。
- gitleaks 已在 ci.yml `secret-scan` job（`gitleaks detect --config .gitleaks.toml`，docs allowlist）。
- **P0 剩余**：分支保护（private + 免费版无法启用，待可见性/计费决策，非代码层面）。
- **P1/P2/G2 状态**：`v0.1.0-rc.2` 已关闭首个合格受控 RC 门禁；`v0.1.0-rc.3` 又以正式 signing
  key、默认分支 CI、远端空仓消费、107/107 签名读回、远端自带 Wrapper 的 Initializer、
  SBOM/checksum/provenance 与 immutable GitHub Release 重新关闭 P2 已发布合同。G2 只剩
  `xq-platform-next` 的真实产品消费、migration replay、升级与回滚。

2026-08-11 `0.1.0-rc.1` 就绪批次 1：端点授权真实装配 + 签名发布 fail-closed
- **端点授权进入真实运行路径**：Servlet Web 宿主在存在 `AuthenticatedPrincipalResolver` 时条件装配
  `AinerRequestAuthorizationManager`、`AinerAuthorizeInterceptor` 与 MVC 注册器。MVC 完成
  `HandlerMethod` 解析后，interceptor 在 controller effect 前执行 `AuthorizationService`；拒绝统一映射
  为 `AINER.COMMON.FORBIDDEN`/HTTP 403。真实 RSA 签名 JWT 门禁覆盖匹配 scope 放行、缺 scope 403 且
  effect 不发生、无 Bearer 401；PUBLIC 匿名投影已补单测，带未执行 obligation 仍保持 deny。
- **发布工作流 fail-closed**：tag 只接受语义化非 SNAPSHOT 版本，锁定 Maven 3.9.16 并校验 SHA-512，
  依次执行 Maven 3/4 Golden Consumer、Initializer consumer、`-Prelease clean deploy`、零跳过检查和
  provenance。所有 tag 发布必须显式设置 `AINER_RELEASE_SIGNING=true` 并提供 GPG key/passphrase；
  passphrase 只经环境变量传入。parentless `ainer-dependencies` BOM 已拥有独立签名 profile。
- **本地签名能力彩排**：一次性 GPG home/临时密钥下，BOM 生成 2 个 `.asc`，`ainer-core` 生成主制品、
  sources、Javadoc 与 POM 等 5 个 `.asc`；临时密钥目录已删除。该结果只证明配置可执行，不代表正式
  signing key、远端 tag 或可信发布身份已经建立。
- **非 SNAPSHOT 消费门禁**：以 `AINER_VERSION=0.1.0-rc.1` 在隔离仓库安装 19 个制品；修正消费者脚本
  两次可重复性构建的 `gpg.skip` 参数不一致后，artifact comparison 全匹配，Maven 3.9+ 与 Maven 4
  外部授权 Golden Consumer 均为 1 test / 0 failure / 0 error / 0 skipped。
- **Initializer consumer**：同一 `0.1.0-rc.1` 坐标下，普通、PostgreSQL、CRUD 三个变体两轮生成一致，
  独立编译成功；普通模板 2 tests，PostgreSQL/CRUD 使用 PostgreSQL 18.3 Testcontainers，CRUD 汇总
  4 tests，均为 0 failure / 0 error / 0 skipped。
- **全量验证**：仓库 Wrapper（Maven 4.0.0-rc-6 + JDK 25）执行 `./mvnw clean verify`，19 模块
  BUILD SUCCESS；`scripts/check-surefire-results.sh` 为 **299 tests / 0 failure / 0 error / 0 skipped**。
  release workflow YAML 可解析，`git diff --check` 通过。
- **证据边界**：源码版本仍为 `0.1.0-SNAPSHOT`，本批尚未提交/推送，未产生最新 commit 的远端 CI/
  gitleaks、正式签名 key、tag、GitHub Packages 制品、provenance 或产品回滚记录。Maven 4 reactor 内
  BOM import 的 source-model 告警仍按 ADR-0026 作为待批准例外/后续模型决策处理。最后一项 principal
  resolver fail-closed 收紧后，全 reactor 与 7 项 manager 单测已重跑通过；重复执行隔离 non-SNAPSHOT
  consumer 时，Maven model builder 等待远端仓库 TLS 超过 7 分钟、尚未进入 reactor，人工终止并清理
  临时目录，因此不能把这次重复运行计为新的 consumer 成功证据，仍以同轮前一次完整通过记录为准。

2026-08-11 ADR-0037 post-Greenfield 授权基线 + AuthorizationManager 端点级适配器
- **ADR-0037 Accepted**：正式取代 ADR-0030（标记 Superseded）。以 post-Greenfield Workspace 语义
  重述（`Scope.Workspace/Resource/Global`、membership-independent `USER_NEUTRAL_V1`、
  `workspaceId` 归属）。继承 ADR-0030 的 grant-path 真值表与 RBAC+ReBAC+ABAC 组合（不重复设计）。
- **adapter 包归属决策**（解决边界矛盾）：adapter 首版放在 `ainer-module-authorization` 的 `spring/`
  子包（适配边界）。`domain/`/`policy/`/`catalog/`/`application/` 保持 Spring-free（ArchUnit 守护）。
  `ainer-starter-security` 不加 authorization 依赖（维持 §9.3）。第二个重复装配消费者后评估独立制品。
  pom 新增 compile scope `spring-security-core` + `spring-security-web`。
- **adapter 实现**：`AinerRequestAuthorizationManager`（`AuthorizationManager<RequestAuthorizationContext>`，
  注入 `AuthorizationService` + `AuthenticatedPrincipalResolver`）、`AinerAuthorizationResult`（implements
  Spring `AuthorizationResult`，ALLOW+空 obligations→grant，其余→deny）、`@AinerAuthorize` 注解 +
  `AinerAuthorizeInterceptor`（设 request attribute）。5 项单元测试覆盖 ALLOW/DENY/未认证/PUBLIC+obligation/
  无注解 fallthrough。
- **首版限制**（标注后续切片）：方法级 AOP、RFC 9470 challenge handler、DecisionObligationExecutor、
  AuthorizationTargetResolver 产品注册机制均未实现。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：**295 tests / 0 failure / 0 error /
  0 skipped**（较上轮 290 + 5 新增 adapter 单元测试）。零回归。
- 新增代码注册、版本化 `GrantAdministrationPolicy`：宿主显式声明精确可信 SERVICE 与 assignable
  Permission/Scope/target；没有策略时 `GrantAdministrationGuard` 使用内建 deny-all，
  `authorization.manage` scope 不再自动产生管理权。
- Controller 的全部管理读取与写入先校验 guard；Role/Binding application service 在事务边界再次
  校验，直接调用服务也不能绕过。硬性拒绝 system-only/策略外 Permission、GLOBAL/策略外 Scope、
  越界 target、自建/自撤 Binding，以及修改自己的任一 ACTIVE Binding 所引用 Role。
- 真实 RSA 签名 JWT + PostgreSQL 18.3 HTTP 矩阵新增 4 项：任意持 scope SERVICE 拒绝、不可授予与
  system-only Permission 拒绝、GLOBAL 与越界 target 拒绝、自 Binding/自 Role 修改拒绝；另增 1 项
  无宿主策略默认拒绝单测。
- Greenfield 已移除 Tenant，未复活 ADR-0030 的 tenant OWNER bootstrap；生产管理主体、Ainer Admin
  与产品 onboarding/bootstrap 仍需由 post-Greenfield 取代 ADR 定义。模块当前安全默认是“不可管理”，
  不是“任意 SERVICE 可管理”。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：**278 tests / 0 failure / 0 error /
  0 skipped**（较上轮 273 + 4 项 HTTP 防提权矩阵 + 1 项默认 deny-all 单测）。`git diff --check`
  通过，缺陷 10 标记为模块级修复。

2026-08-11 真实 JWT 端到端测试：替换 stub Principal（缺陷 9 修复）
- **改造 `AuthorizationManagementHttpTest`**：删除 stub `AuthenticatedPrincipalResolver`，启用
  `ainer.security.resource-server.enabled=true`。新增测试 `JwtDecoder` bean：测试生成 RSA 3072 密钥对，
  `NimbusJwtDecoder.withPublicKey` 验签 + issuer/audience validator（issuer=`https://auth.ainer.test`，
  audience=`ainer-api`）。用 Nimbus `SignedJWT` + `RSASSASigner` 签发 SERVICE_V1 JWT（带
  `token_profile`/`claim_contract_version`/`actor_type`/`scope` claims），客户端注入 Bearer header。
- **整条链路真实**：SecurityFilterChain → NimbusJwtDecoder 验签 → JwtToVerifiedJwtClaims →
  ReferenceTokenProfileResolver（解析 SERVICE_V1）→ SecurityContextAuthenticatedPrincipalResolver →
  Controller requireManagement。新增 2 项负向测试：无 Bearer → 401、SERVICE 缺 scope → 403。
- `AuthorizationPersistenceIntegrationTest` 补充 resource-server enabled + 最小 fake JwtDecoder（该测试
  测持久化切片不测 HTTP，真实 JWT 由 HttpTest 覆盖）。
- pom 加 `ainer-starter-security` + `spring-security-oauth2-jose` test scope（Nimbus 传递依赖）。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：**273 tests / 0 failure / 0 error /
  0 skipped**（较上轮 270 + 3 新增负向 JWT 测试）。零回归。缺陷 9 标记修复。

2026-08-11 授权审计四层写入：change/decision audit 接入（缺陷 3 修复）
- **change_audit**：新建 `AuthorizationChangeAudit`（domain record）+ port + `AuthorizationChangeAuditService`
  （同事务 `@Transactional`，审计失败回滚，ADR §11.7）+ mybatis impl/mapper/row/XML。接入
  `RoleApplicationService.createRole`/`replacePermissions` 与 `SubjectBindingApplicationService.createBinding`/
  `revokeBinding` 的写方法（actor 从 `AuthenticatedPrincipal` 提取）。Controller 传入 actor + requestId。
- **decision_audit**：新建 `AuthorizationDecisionAudit` + port + `AuthorizationDecisionAuditService`
  （`@Transactional(REQUIRES_NEW)`，调用方按 AuditLevel 触发，ADR §12.4）+ mybatis 四层。
  `AuthorizationService` 保持 Spring-free 纯决策器，调用方（未来的 SecurityManager adapter 或应用服务）
  在决策后显式调 `recordIfApplicable`。Anonymous/PUBLIC 决策不在此记录（表要求 requester NOT NULL）。
- 真实 PostgreSQL 18.3 Testcontainers 验证：Role 生命周期测试断言 change_audit 写入 CREATE +
  REPLACE_PERMISSIONS 两条记录；新增 decisionAudit 测试验证 DENY 决策正确写入。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：**270 tests / 0 failure / 0 error /
  0 skipped**。零回归。缺陷 3 标记修复。P0 级阻塞缺陷 1-5 + 装配缺陷 6-8 + 审计缺陷 3 全部闭合。

2026-08-11 授权模块生产装配：AuthorizationService 成为 ainer-server 可用 Bean（缺陷 6/7/8 修复）
- **归属决策**：授权决策与管理 API 归属 `ainer-server`（业务 Resource Server），依据链：ADR-0030 §9.6
  单体装配、§2.2 授权模块不解析 JWT（主体投影唯一来源是 RS 侧 `ainer-starter-security` 的
  `AuthenticatedPrincipalResolver`，仅在 `resource-server.enabled=true` 时注册）、`database.md` 将
  V202608070340 归业务库 `ainer`。
- **装配实现**：`AuthorizationModuleConfiguration` 新增 `authorizationService` @Bean（注入 PermissionRegistry/
  ScopePermissionCeiling/PublicAccessPolicy/DomainAuthorizationPolicy/BindingResolver + policyVersion
  配置属性）+ 三个 deny-all 默认 @Bean（全部 `@ConditionalOnMissingBean`，保证未配置时端到端默认拒绝）。
  `ainer-server` pom 加 `ainer-module-authorization` 依赖，主类 @Import `AuthorizationModuleConfiguration`，
  V202608070340 migration 随之在 ainer 库执行。`DefaultQueryAuthorizationPlanner`（泛型 `<I,Q>`）按设计
  由产品模块实例化，不在 Ainer 装配。Spring Security `AuthorizationManager` adapter（ADR §8.2）属后续。
- ainer-server 冒烟测试（`AinerServerApplicationTest`/`AinerServerMetricsSecurityTest`）增加
  `ainer.authorization.enabled=false`（排除 DataSource 时跳过 @Repository 装配）。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：**269 tests / 0 failure / 0 error /
  0 skipped**。零回归。缺陷 6/7/8 标记修复。

2026-08-11 接手复核：WIP 拆清、ADR/状态文档纠正、Docker 修复、授权 P0 缺陷修复
- **核实**：逐行核实交接点名的全部技术证据，确认交接文档判断正确（探查代理只读文档得出的
  「S0–S3 已落地」结论错误）。详见本节「ADR-0030 授权模块实现差距清单」。
- **文档纠正**：ADR-0030 回退 Accepted→Proposed（决策文本仍以 tenant 模型为主，与 Greenfield
  地基冲突）；`decisions/README.md`、`architecture.md`、`00-overview.md`、`README.md`、`CHANGELOG.md`、
  `project-status.md` 同步纠正「S0–S3 全部落地/门禁通过」的错误表述；删除孤岛 handoff 文档
  （`docs/architecture/2026-08-11-session-handoff-*.md`，未索引且结论与事实矛盾）。
- **Docker 修复**：Dockerfile 修正模块产物路径（`target/`→`${AINER_MODULE}/target/`）；
  移除 BuildKit `--mount`（Colima 等 daemon 无 buildx），改传统层缓存 + go-offline 预热；
  `init-db.sh` 密码改用 psql 变量 `:'var'` 安全转义；compose full profile 诚实标注实验性限制。
  完整镜像构建受容器内 Maven preview distribution SHA-256 校验限制（环境问题，已文档化）。
- **授权 P0 缺陷修复**（真实 PostgreSQL 18.3 Testcontainers 验证）：
  - RESOURCE scope CHECK 冲突：`Scope.Resource` 加 workspaceId，全链路同步（domain/infra/API/测试）
  - systemOnly PUBLIC 绕过：PUBLIC 分流前加 systemOnly 检查 + 负向测试
  - Role.name 死参数：`Role` record 加 name 字段，全链路同步（含 PostgresBindingResolver 改用 roleRecord.role()）
  - RoleResponse 时间戳假数据：RoleRecord 加 createdAt/updatedAt，从 DB 读取
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：**269 tests / 0 failure / 0 error /
  0 skipped**（较接手前 268 + 1 systemOnly PUBLIC 负向测试）。`git diff --check` 通过。
- **明确列为后续**：缺陷 3（审计四层写入）、授权模块生产装配、真实 JWT 端到端测试、管理 API
  防提权矩阵、外部 Golden Consumer 真正消费 AuthorizationService、新增取代 ADR-0030 的 post-Greenfield ADR。

2026-08-11 通用授权 S3 查询计划与 Golden Consumer 验证（ADR-0030 S3）
- 新增集合查询授权契约（ADR-0030 §7 S3）：`QueryAuthorizationRequest<I>`（类型化查询请求，
  携带产品定义的 query intent）、`AuthorizedQueryPlan<Q>`（sealed interface：`Allowed<Q>` 携带
  产品定义的类型化查询约束 + obligations；`Denied<Q>` 携带稳定 reason code）、
  `QueryAuthorizationPlanner<I,Q>` 端口、`QueryConstraintBuilder<Q>`（产品实现的约束累积器）。
- 新增 `DefaultQueryAuthorizationPlanner`：复用 S0 scope ceiling / permission registry /
  binding resolver 逻辑，遍历 live bindings 调用产品 `QueryConstraintBuilder.accumulate()` 生成
  类型化 `Q`。RELATION_DERIVED 路径拒绝（需要 per-resource facts，不适合集合查询）；
  无 contributing binding 时返回 Denied（无 ALLOW 缓存）。
- Ainer **不输出 SQL**：`Q` 是产品定义的类型（如 `ListingReadConstraint(global, allowedWorkspaceIds,
  allowedResourceIds)`），产品 Repository/search adapter 翻译为参数化 PostgreSQL 过滤。
  未授权 row 在数据库层排除，不先加载到 JVM 再过滤。
- Golden Consumer 查询验证（6 项纯单元测试，无 Docker 依赖）：operator + Workspace binding →
  受约束查询计划（allowedWorkspaceIds 含该 workspace）；operator + Resource binding → 精确
  resource 约束；SERVICE + Global binding → 全局约束（global=true）；customer 无 binding →
  Denied；REVOKED binding → Denied（撤销立即生效）；wrong scope → Denied。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：269 tests / 0 failure /
  0 error / 0 skipped（较 S2 基线 262 + 6 新增 S3 查询计划测试 + 1 接手复核新增 systemOnly
  PUBLIC 负向测试）。
- **⚠️ 接手复核（2026-08-11）**：上述「ADR-0030 S0+S1+S2+S3 全部完成」「门禁 8/10 通过」的结论**已被
  证伪**。S1–S3 仅为原型，未达 ADR-0030 验收。ADR-0030 已回退为 Proposed。§13.4 创建门禁 8/10
  **未关闭**。已核实的实现差距见下方「ADR-0030 授权模块实现差距清单」。

2026-08-11 通用授权 S2 管理 REST API 闭环（ADR-0030 S2）
- 新增 `ainer-module-authorization` 管理 REST API 层（`/api/authorization/**`）：
  `AuthorizationManagementController`（`@RestController @RequestMapping("/api/authorization")`）+
  `AuthorizationApiDtos`（Role/Binding/Permission/EffectiveAccess 请求与响应 record，含
  `from(domain)` 工厂方法）。POM 增加 `ainer-starter-web` 依赖与 `ainer-test-support` 测试依赖。
- 端点：`GET /permissions`（目录投影只读）、`POST /roles` + `GET /roles/{id}` +
  `PUT /roles/{roleId}/permissions`（角色与权限管理）、`POST /bindings` + `GET /bindings/{id}` +
  `POST /bindings/{bindingId}/revocations`（绑定生命周期，撤销用 action-path noun 而非 DELETE）、
  `GET /effective-access?issuer=&subjectType=&subjectId=`（查询某 subject 当前有效绑定）。
- 安全模型：所有端点要求 SERVICE principal + `authorization.manage` scope（`requireManagement`
  guard）。Human principal 与缺 scope 返回 403。未注册的 permission code 不能分配给 Role（422）。
- 5 项 HTTP 集成测试全绿（TestRestTemplate + 真实 PostgreSQL 18.3）：创建/查询 Role、
  替换 Role 权限、绑定创建/撤销/Effective Access 立即反映、非法 scopeKind 返回 422、
  Permission 目录列表。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：262 tests / 0 failure /
  0 error / 0 skipped（较 S1 基线 257 + 5 新增 S2 HTTP 测试）。
  > **接手复核（2026-08-11）**：下句「S3 仍未实现」已被随后提交的 S3 原型推翻；但 S3 原型仍未达验收，
  > 详见上方 S3 记录的复核注释与本节「ADR-0030 授权模块实现差距清单」。Spring Security
  > `AuthorizationManager` adapter（方法级 `@AinerAuthorize`）、OpenAPI/SDK 生成、Ainer Admin 集成确属
  > 后续未实现项。

2026-08-11 通用授权 S1 PostgreSQL 持久化最小闭环（ADR-0030 S1）
- `ainer-module-authorization` 从纯域模块（ainer-core + jspecify）升级为可持久化模块：新增
  `ainer-starter-persistence` + `ainer-security` 依赖与 application/infrastructure 分层。
- 新增 Flyway migration `V202608070340__authorization_foundation_baseline.sql`（6 张表）：
  `ainer_authorization_permission`（目录投影）、`ainer_authorization_role`（角色聚合）、
  `ainer_authorization_role_permission`（角色-权限关联，复合 PK）、
  `ainer_authorization_subject_binding`（绑定生命周期）、`ainer_authorization_change_audit`
  （变更审计 append-only）、`ainer_authorization_decision_audit`（决策审计 append-only）。
  scope_kind CHECK 适配 Greenfield 后 Workspace 语义：GLOBAL（全 NULL）/ WORKSPACE
  （workspace_id 非空）/ RESOURCE（workspace_id + resource_type + resource_id 全非空）。
- 新增 application 层：`RoleRepository`/`SubjectBindingRepository`/`PermissionCatalogRepository`
  端口 + `RoleApplicationService`/`SubjectBindingApplicationService` + `AuthorizationErrorCode`
  （`AINER.AUTHORIZATION.*` 稳定错误码）。未注册的权限不能分配给角色（fail-closed）。
- 新增 infrastructure 层：`RoleRow`/`PermissionRow`/`SubjectBindingRow` POJO +
  `RoleMapper`/`SubjectBindingMapper`/`PermissionMapper`（`@Mapper` + 显式 XML SQL）+
  `MybatisRoleRepository`/`MybatisSubjectBindingRepository`/`MybatisPermissionCatalogRepository`
  适配器 + `PostgresBindingResolver`（实现 S0 `BindingResolver` 端口，取代内存 fixture）。
  ID 使用 PostgreSQL 18 `DEFAULT uuidv7()` + `INSERT ... RETURNING id`（`<select>` + resultType=UUID）。
- 撤销语义验证：`bindingService.revokeBinding` 后 `bindingResolver.liveBindings` 立即不返回该
  绑定——无 ALLOW 缓存，仍有效的 JWT 不能恢复已撤销的数据库授权。过期绑定同样在 SQL 层排除。
- 集成测试 9 项全绿（Testcontainers `postgres:18.3-alpine`）：空库 migration 创建 6 张表、
  Role CRUD + 权限原子替换 + 版本检查、重复 code fail-closed、未注册权限拒绝、绑定创建/撤销/
  resolver 立即反映、过期绑定排除、scope CHECK 拒绝 GLOBAL+workspace 与 WORKSPACE+resource
  非法组合、resolver 产出 domain SubjectBinding（含 role permissions）。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：257 tests / 0 failure /
  0 error / 0 skipped（较 T1-7 基线 248 + 9 新增 S1 集成测试）。
  > **接手复核（2026-08-11）**：上句「ADR-0030 状态从 Proposed 转 Accepted」已撤销——ADR-0030 回退为
  > Proposed，因为以下差距清单显示实现未达验收。

### ADR-0030 授权模块实现差距清单（2026-08-11 接手复核）

下列各项已通过逐行核实源码确认（非文档推断）。S0 纯决策器可保留为「已落地」，S1–S3 整体判定为
「原型已提交，未达 ADR-0030 验收」。

**P0 级阻塞缺陷（安全/正确性）**：

| # | 缺陷 | 状态 | 证据 | ADR 条款 |
|---|---|---|---|---|
| 1 | **RESOURCE scope 持久化违反 CHECK** | ✅ 已修复（2026-08-11） | migration `ck_..._scope_resource` 要求 `workspace_id`/`resource_type`/`resource_id` 三列全 NOT NULL；原 `applyScope()` RESOURCE 分支写死 `workspaceId=null` 且 `Scope.Resource` 不含 workspaceId。修复：`Scope.Resource` 加 workspaceId 字段，全链路同步，真实 PG 验证通过 | §10 Scope CHECK |
| 2 | **`systemOnly` 权限可经 PUBLIC 路径绕过** | ✅ 已修复（2026-08-11） | 原 `AuthorizationService.decide()` 在检查 systemOnly 前分流到 `decidePublic()`。修复：PUBLIC 分流前加 systemOnly 检查，附负向测试 | §5.1/§3.1 |
| 3 | **change/decision audit 只有表，零写入路径** | ✅ 已修复（2026-08-11） | 原 `src/main` 对两张 audit 表零引用。修复：新建 `AuthorizationChangeAuditService`（同事务，审计失败回滚）接入 RoleApplicationService/SubjectBindingApplicationService 写方法；新建 `AuthorizationDecisionAuditService`（`REQUIRES_NEW`，调用方按 AuditLevel 触发）供调用方记录 authenticated 决策。四层结构（domain record + port + service + mybatis impl + mapper + XML）各建一套，复刻 workspace 审计范式。真实 PG 验证写入正确 | §11.7/§12.4 |
| 4 | **`Role.name` 是完全死参数** | ✅ 已修复（2026-08-11） | 原 `new Role(code, permissions)` 丢弃 name；DB name 列存 code 值。修复：`Role` record 加 name 字段，全链路同步，真实 PG 验证 name 正确存取 | §4.1 |
| 5 | **`RoleResponse` 时间戳是 API 层假数据** | ✅ 已修复（2026-08-11） | 原 `RoleResponse.from()` 用 `Instant.now()` 生成时间戳。修复：`RoleRecord` 加 createdAt/updatedAt，从 DB 读取，真实 PG 验证 replacePermissions 后 updatedAt 刷新 | §11.8 |

**装配级缺陷（决策器不可用）**：

| # | 缺陷 | 状态 | 证据 |
|---|---|---|---|
| 6 | **`AuthorizationService` 无生产装配** | ✅ 已修复（2026-08-11） | 原 Configuration 不声明 AuthorizationService bean。修复：`AuthorizationModuleConfiguration` 新增 `authorizationService` @Bean（`@ConditionalOnMissingBean`，注入 5 个依赖 + policyVersion 配置属性）。`DefaultQueryAuthorizationPlanner` 仍不装配（泛型 `<I,Q>`，按设计由产品模块实例化） |
| 7 | **`DomainAuthorizationPolicy`/`PublicAccessPolicy`/`ScopePermissionCeiling` 无生产装配** | ✅ 已修复（2026-08-11） | 原 Configuration 不声明，无默认实现。修复：新增三个 deny-all 默认 @Bean（全部 `@ConditionalOnMissingBean`），保证未配置时端到端默认拒绝。产品模块覆盖即可提供真实策略 |
| 8 | **三个可执行应用均不依赖授权模块** | ✅ 部分修复（2026-08-11） | 原 ainer-server/authorization-server/offstate-app 均无 pom 依赖、无 @Import。修复：ainer-server pom 加 `ainer-module-authorization` 依赖，主类 @Import `AuthorizationModuleConfiguration`；V202608070340 migration 随之在 ainer 库执行。authorization-server/offstate-app 不装配（归属依据：ADR §9.6 单体装配 + §2.2 授权模块不解析 JWT + database.md 业务库归属） |

**验收级缺陷（ADR 验收项未达成）**：

| # | 缺陷 | 证据 | 对应 ADR 验收项 |
|---|---|---|---|
| 9 | **HTTP 测试用 stub Principal 绕过真实 JWT** | ✅ 已修复（2026-08-11）— 原 `TestPrincipalResolver` 固定返回。修复：重写 `AuthorizationManagementHttpTest`，删除 stub resolver，启用 resource-server，提供真实 `NimbusJwtDecoder`（测试 RSA 公钥验签 + issuer/audience validator），用 Nimbus `SignedJWT` 签发 SERVICE_V1 JWT（带 `token_profile`/`actor_type`/`scope` claims），客户端带 Bearer header。整条链路真实：SecurityFilterChain → NimbusJwtDecoder 验签 → JwtToVerifiedJwtClaims → ReferenceTokenProfileResolver → Controller。新增 2 项负向测试（无 Bearer → 401、缺 scope → 403） |
| 10 | **管理 API 缺防提权矩阵** | ✅ 模块级已修复（2026-08-11）— 新增版本化 `GrantAdministrationPolicy` + `GrantAdministrationGuard`；无策略 deny-all，scope 不能单独授权管理；Controller 与事务服务双层校验 assignable Permission/Scope/target，硬拒绝 system-only、GLOBAL、自 Binding 和修改自己 ACTIVE Binding 所引用 Role。真实 JWT + PG 负向矩阵覆盖。Greenfield 后生产 bootstrap/Ainer Admin 仍属门禁 9 未完成项 | §11.3/§11.5 |
| 11 | **外部 Golden Consumer 仅编译期 smoke** | ✅ 已修复（2026-08-11）— `scripts/verify-maven-consumers.sh` 在独立临时项目中只通过 BOM 与隔离仓库已安装制品定义产品 Permission/Role/Binding/query intent/constraint，实际调用 `AuthorizationService` 与 `DefaultQueryAuthorizationPlanner`；Maven 3.9+、Maven 4 各执行 1 项 JUnit，均为 1 test / 0 failure / 0 error / 0 skipped。当前验证对象仍是本地 `0.1.0-SNAPSHOT`，不宣称不可变发布制品或完整产品场景验收 | 门禁 8 外部 Golden Consumer 验证 |
| 12 | **缺真实参数化 SQL 查询验证** | ✅ 已修复（2026-08-11）— 新增 test-scope 产品表与 `ProductListingQueryAdapter`：在真实 PostgreSQL 18.3 中把 planner 生成的 Workspace/resource `Q` 绑定为 `varchar[]`/`uuid[]` PreparedStatement，一次查询只返回授权 Workspace row；DENY 执行 0 次产品查询，注入形态 status 不扩大结果；20,003 行夹具的 `EXPLAIN (ANALYZE, BUFFERS)` 命中 `idx_consumer_listing_authorized_search`。这证明 Golden Consumer adapter 契约，不宣称已有生产产品 Repository 或生产容量结论 | §7.4/§7.5 |
| 13 | **缺「撤销后原 Token 无法继续业务写」端到端** | ✅ 模块级已修复（2026-08-11）— 真实 RSA 签名 `USER_NEUTRAL_V1` JWT 先经产品所有的 test-scope HTTP 路径完成受保护写；可信 SERVICE 通过真实管理 API 撤销 PostgreSQL Binding 后，复用完全相同且仍有效的 JWT 再写返回 403，产品事件仍为 1。产品服务从产品表解析资源所属 Workspace，每次调用 `AuthorizationService`/`PostgresBindingResolver`，无 ALLOW cache；ALLOW 与 `NO_BINDING` DENY 均在 effect 前持久化审计。外部消费者与生产失效 SLA 尚未验收 | 门禁 10 工程链路 |

**§13.4 创建门禁状态**：门禁 8（外部 Golden Consumer 验证）**仍未关闭**。外部 Maven 制品消费与
真实 `AuthorizationService`/查询规划器调用这一工程维度已补齐，但仍缺不可变已发布制品、ADR 要求的
完整产品关系/双向独立负例，以及真实 HTTP public row/字段投影验证。test-scope 产品 adapter 的参数化
PostgreSQL 行过滤维度已补齐；门禁 9（Ainer Admin 管理 + Effective Access）**未关闭**（模块防提权
矩阵已补，但 Admin UI 与 post-Greenfield 生产 bootstrap 未集成）；门禁 10（撤销后受保护写失效）
的**仓内工程链路已通过**，但发布级门禁仍未关闭：还需由外部消费者在目标部署拓扑中批准授权失效
SLA，并验证跨实例/缓存/传播边界。

**ADR-0030 文本与 Greenfield 地基冲突**：ADR-0030 仍以 tenant 模型为主
（`credentialTenantId`、`TENANT(tenantId)` scope、I0 切片的「allowlisted consumer client 无 tenant
USER Token」），而 ADR-0033 Greenfield 已完全移除 tenant。实现已迁 Workspace。完整重述需新增取代 ADR。

**后续批次进展（当前接手轮持续推进）**：
1. ~~修复 P0 缺陷 1/2/4/5~~（已完成）；~~修复装配缺陷 6/7/8~~（已完成）；~~修复缺陷 3（审计四层写入）~~（已完成）；
2. ~~Spring Security `AuthorizationManager` adapter（ADR-0037 §4，`@AinerAuthorize` 端点级 opt-in）~~（已完成）；
   方法级 AOP、RFC 9470 challenge handler、DecisionObligationExecutor、AuthorizationTargetResolver 产品注册机制属后续；
3. ~~真实 JWT 端到端测试~~（已完成）；
4. ~~管理 API 模块级防提权矩阵~~（已完成）；post-Greenfield 生产 bootstrap/Ainer Admin 仍待门禁 9；
5. ~~外部 Golden Consumer 真正消费 AuthorizationService/查询规划器~~（本地 SNAPSHOT 制品工程门禁已完成）；
   不可变发布制品与完整产品关系/投影场景仍属于门禁 8；
6. ~~产品 adapter 将类型化 `Q` 下推为参数化 PostgreSQL 并验证 row/查询次数~~（已完成）；真实产品 Repository 与 HTTP 字段投影仍属于门禁 8；
7. ~~撤销 Binding 后复用原 USER Token 验证受保护业务写失败~~（已完成）；外部消费者与生产授权失效 SLA 仍属于门禁 10；
8. ~~新增取代 ADR-0030 的 post-Greenfield 授权基线 ADR（ADR-0037 Accepted）~~（已完成）。

2026-08-11 撤权后原 Token 受保护写失效闭环（缺陷 13）
- 扩展 `AuthorizationManagementHttpTest`：新增 test-scope 产品资源表、产品写事件表与受保护 HTTP
  写路径。产品服务先从自己的表读取资源所属 Workspace，再构造单资源 `AuthorizationRequest`；
  Ainer 仍不知道产品表、字段和 effect 语义。
- 用测试 RSA 私钥签发 `USER_NEUTRAL_V1` JWT，Resource Server 以对应公钥真实验签，再经
  `ReferenceTokenProfileResolver` 取得 `iss/sub/scope`。第一次写由 Workspace Binding ALLOW；可信
  `SERVICE_V1` 管理主体通过 `/api/authorization/bindings/{id}/revocations` 提交撤销。
- 第二次请求复用**完全相同的序列化 JWT**，仅数据库 Binding 状态变化；`PostgresBindingResolver`
  每次重新查询后返回无 live Binding，HTTP 为 403，产品写事件计数保持 1。
- 产品 effect 之前调用 `AuthorizationDecisionAuditService`；ALLOW/`AUTHORIZED` 与
  DENY/`NO_BINDING` 各存在 1 条审计。DENY 审计使用 `REQUIRES_NEW`，在业务事务回滚后仍保留。
- 定向验证：`AuthorizationManagementHttpTest` **13/0/0/0**。全量 `./mvnw clean verify`
  （JDK 25 + PostgreSQL 18.3 Testcontainers）通过：**290 tests / 0 failure / 0 error / 0 skipped**；
  `check-surefire-results.sh` 与 `git diff --check` 通过。
- 边界：这是单进程、仓内 test-scope 产品路径的工程验证，不等同于外部产品接入、跨实例传播测试，
  也不批准生产授权失效 SLA；因此门禁 10 的发布级状态仍未关闭。

2026-08-11 Golden Consumer 参数化 PostgreSQL 查询闭环（缺陷 12）
- 新增 `GoldenConsumerPostgresQueryIntegrationTest`。产品定义的 listing 表、query intent、类型化
  `ListingReadConstraint`、字段投影与 JDBC adapter 全部位于测试消费者边界；Ainer 生产代码仍不包含
  产品表名、列名或 SQL。
- adapter 使用固定 SQL + PostgreSQL `varchar[]`/`uuid[]` PreparedStatement 参数，将 Workspace/resource
  授权约束和已校验状态意图同时下推；只选择公开投影列，不加载 `internal_cost`，不在 JVM 二次过滤。
  Workspace A 的 PUBLISHED 查询不会返回 Workspace B row；注入形态 status 返回空集且表数据不变。
- ALLOW 的产品数据查询严格 **1 次**，DENY 为 **0 次**，避免逐 row N+1；类型化 ID 集合上限为 100，
  空约束与未消费 obligation 失败关闭。
- 20,003 行合成夹具执行 `ANALYZE` 后，真实 `EXPLAIN (ANALYZE, BUFFERS)` 命中
  `idx_consumer_listing_authorized_search`。这是确定性测试规模的查询计划证据，不外推生产容量或延迟。
- 同步加固 `DefaultQueryAuthorizationPlanner`：resolver 即使错误返回其他主体、过期 Binding、USER
  GLOBAL 或错 resourceType 的 RESOURCE Binding，也不能贡献 `Q`；`QueryConstraintBuilder` 首次
  `current=null` 现已在公开 nullness 契约中显式表达，Allowed constraint/obligations 均不可为 null。
- 定向验证：真实 PostgreSQL 适配器 7 tests + planner 10 tests，**17/0/0/0**。
- 全量 `./mvnw clean verify`（JDK 25 + PostgreSQL 18.3 Testcontainers）通过：
  **289 tests / 0 failure / 0 error / 0 skipped**；`check-surefire-results.sh` 与 `git diff --check` 通过。
- 公开 query 契约变更后重新执行 `verify-maven-consumers.sh`：19 模块制品/可复现构建比较通过，
  Maven 3.9+ 与 Maven 4 外部授权 JUnit 各 **1/0/0/0**。首次冷仓下载因 Maven Central TLS
  `bad_record_mac` 中断，增加 transport retry 的完整重跑成功；该瞬时网络失败未发生在编译或测试阶段。

2026-08-11 外部授权 Golden Consumer 制品门禁补强（缺陷 11）
- `scripts/verify-maven-consumers.sh` 生成独立临时 Maven 项目，不复制 Ainer 源码，只从隔离本地仓库
  消费 `ainer-dependencies` BOM 与 `ainer-module-authorization` 等公开坐标。
- 外部测试由消费者自行定义 `consumer.offer.read`、资源、Role/Workspace Binding、领域 policy、
  query intent 与类型化 constraint；真实调用 `AuthorizationService` 验证同 Workspace ALLOW/跨
  Workspace DENY，并调用 `DefaultQueryAuthorizationPlanner` 验证受约束集合计划/无 Binding DENY。
- 系统 Maven 3.9+ 与 Wrapper Maven 4.0.0-rc-6 均执行 `clean verify`；脚本强制读取 Surefire XML，
  两轮各为 **1 test / 0 failure / 0 error / 0 skipped**。完整 19 模块制品安装、sources/javadoc、
  consumer POM、配置元数据与可复现构建比较同轮通过。
- 同轮随后使用 JDK 25 + PostgreSQL 18.3 Testcontainers 完成全量 `./mvnw clean verify`：
  **278 tests / 0 failure / 0 error / 0 skipped**；`check-surefire-results.sh` 与 `git diff --check` 通过。
- 证据边界：本轮消费的是隔离仓库中的 `0.1.0-SNAPSHOT`，证明公开 Java 契约与 Maven 3/4 工程可消费性；
  不证明正式仓库发布、不可变 RC、真实产品关系/投影、参数化 SQL 或发布就绪。

2026-08-10 Docker Compose 开发环境落地：一键启动 PostgreSQL 双库 + 完整应用栈
- 新增 `docker-compose.yml`（仓库根）、`Dockerfile`（多阶段构建）、`docker/init-db.sh`
  （PostgreSQL 双库双用户初始化：`ainer`/`ainer_auth`）、`scripts/generate-dev-keys.sh`
  （幂等生成 RSA 3072 PKCS#8 PEM 签名密钥到 `secrets/dev-keys/`）、`.env.example`
  （完整环境变量模板，占位符值，无真实密钥）。
- Compose 分两个 profile：默认只启动 `postgres`（日常开发多数场景只需数据库），
  `--profile full` 额外构建并启动 Authorization Server + 业务 Server。应用通过
  `Dockerfile` 的 `AINER_MODULE` build arg 选择目标模块，容器内使用仓库 Maven Wrapper
  构建（遵守 AGENTS.md 生产者构建规则）。
- 验证结果（Colima Docker）：`docker-compose config` 通过；默认 profile 只暴露 postgres，
  full profile 含 3 个 service；`generate-dev-keys.sh` 首次生成 + 二次幂等跳过正常，
  产出 PKCS#8 PEM 3072 位密钥对；postgres 容器启动 8s 内 healthy，init-db.sh 正确创建
  `ainer`（owner ainer）与 `ainer_auth`（owner ainer_auth）双库，双用户各自可连接
  PostgreSQL 18.3。`secrets/` 与 `.env` 被 `.gitignore` 正确忽略。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：248 tests / 0 failure /
  0 error / 0 skipped，与 T1-7 基线一致，零回归。`git diff --check` 通过。
- 本地 HTTPS issuer 注意：Authorization Server 代码强制要求 issuer 为 `https://` URL
  （`AinerAuthorizationServerConfiguration:96`），Compose 内 AS 容器实际监听 HTTP。
  完整联调如遇 RS 拉取 JWK 因自签证书失败，推荐用 `./mvnw spring-boot:run` 在宿主机
  分别启动两应用、只用 Compose 提供数据库。生产部署仍走 `ops/dev/`（systemd + Let's Encrypt）。

2026-08-10 T1-7 `ainer-test-support` 落地：RestTestClient + `@ServiceConnection` + PostgreSQL 测试基座
- 新增 `ainer-framework/ainer-test-support` 模块（ADR-0029 T1 第 7 项）：`RestTestClient`/`RestResponse`
  基于 Boot 4.1 `TestRestTemplate` 提供 JSON 便捷与 JsonPath 断言；`AinerPostgresContainer` 固定
  `postgres:18.3-alpine` 镜像，配合 Boot `@ServiceConnection`（`JdbcContainerConnectionDetailsFactory`
  via spring-boot-testcontainers + spring-boot-jdbc）自动装配 DataSource，取代 `@DynamicPropertySource`
  样板。模块自身 5 个测试全绿（RANDOM_PORT 集成 + 真实 PG 容器 + 单元）。
- Initializer v1 模板已接入：生成项目 pom 增加 `ainer-test-support` test 依赖；SMOKE/CRUD 测试模板
  改用 RestTestClient 与 `@ServiceConnection` 基座。ProjectGeneratorTest 断言同步（33 tests 全绿）。
- 全量 `./mvnw clean verify`（JDK 25 + Colima）BUILD SUCCESS：248 tests / 0 failure / 0 error /
  0 skipped。`verify-maven-consumers.sh`（19 个 consumer POM、9 个 library 制品 sources/javadoc、
  Maven 3.9/4 双 golden consumer）与 `verify-initializer-consumer.sh`（普通/postgres/CRUD 三通道
  真实 Testcontainers 0 skipped）均通过。
- 关键依赖事实：Boot 4.1 中 `spring-boot-resttestclient` 不传递 `spring-boot-restclient` 与
  `spring-boot-http-client`，test-support 需显式声明；`spring-boot-jdbc` 的
  `JdbcContainerConnectionDetailsFactory` 经 `META-INF/spring.factories` 注册
  `ConnectionDetailsFactory`，`@ServiceConnection` + PG 容器即可生成 JDBC ConnectionDetails。

2026-08-10 P0-5 虚拟线程双模式压测矩阵闭环：等待型场景通过并落地默认开关
- 脚本升级为双场景：JDBC 分页（`/api/metricRows`）与等待型并发（注入
  `/api/wait` 端点模拟外部 IO 阻塞，`Thread.sleep` 阻塞式等待）。
- 等待型实测（80ms × 400 并发，8000 请求两轮复跑一致）：platform
  p50≈167ms/p95≈174ms、2239 req/s；virtual p50≈87ms/p95≈112ms、3954 req/s——
  虚拟线程延迟约减半（-48%）、吞吐 +77%；两轮 p50 差 1ms 内稳定。
- JDBC 分页对比（4000 请求/40 并发）：platform p95=17、virtual p95=16，
  RPS 4378 vs 4406——双模式同级，虚拟线程无性能回归。
- 依据 ADR-0029 决策 5 条件成立，Initializer v1 模板新增
  `spring.threads.virtual.enabled=true` 默认开启；新增
  `generatedProjectsEnableVirtualThreadsByDefault` 断言（33 tests 全绿）；
  真实消费者 `xq-platform-next` 重新生成后在默认虚拟线程下
  clean verify 4 tests 0 skipped 全绿。
- 业务失败两场景均为 0；ab Length 计数保持为连接复用观测伪影
  （此前已单独验证 30 次连续响应长度一致）。

2026-08-10 P0-5 虚拟线程双模式压测矩阵基线（脚本 `scripts/measure-virtual-threads.sh`）
- 在临时目录生成 PostgreSQL CRUD 消费者（`metricRows` 实体，manifest v1），以固定
  Hikari 池 16、Tomcat 线程上限 200 分别启动平台线程（默认）与
  `spring.threads.virtual.enabled=true` 双模式，ApacheBench 压制 `/api/metricRows`
  分页接口（真实 JDBC + Flyway migration + 50 行种子数据）。
- 实测（本机 macOS + Colima, JDK 25）：8000 请求/80 并发下 platform
  p50=11/p90=20/p95=25/p99=68ms、5636 req/s；virtual p50=10/p90=23/p95=30/p99=69ms、
  5890 req/s；两轮复跑（2000/40）趋势一致（virtual p95 19–30ms 与 platform
  20–32ms 同级）。业务失败均为 0（ab 无 Non-2xx 行），Failed 计数为 ab 对
  keep-alive 复用的 Length 观测伪影（已单独验证 30 次连续响应长度一致）。
- JFR 录制 `settings=profile`（platform/virtual 各约 2.3–2.9MB）确认
  jdk.ThreadStart 事件存在（virtual 45 条，含 container-0 容器线程）。
- 结论：等待型 MVC+JDBC 负载下双模式性能同级，虚拟线程无性能回归；按 ADR-0029
  决策 5 先保持默认平台线程，"新 MVC 项目默认 v-thread" 的开关需要更重负载
  （高等待/长阻塞场景）进一步压测后决定。矩阵已接入 CI 独立
  `virtual-thread-matrix` job（Ubuntu apache2-utils 提供 ab），不阻塞主质量门禁。
- 过程中暴露 JDK 25 + PG JDBC 环境事实：`jdbc:postgresql://127.0.0.1`（字面
  IP）经 `InetSocketAddress.createUnresolved` 连接失败（UnknownHostException），
  使用 `localhost` 正常——脚本已固化为 localhost，写入验证记录便于排查。

2026-08-09 首个外部消费者 `xq-platform-next` 出生（P3 前置验证）
- 用 Initializer CLI（manifest v1）在外部独立仓库 `~/01-code/xq/xq-platform-next` 生成
  `platformApp` 实体 CRUD 全栈：独立 `mvn verify`（Maven 3.9，JDK 25，真实 PostgreSQL 18.3
  Testcontainers）4 tests / 0 failure / 0 skipped，BUILD SUCCESS。
- 暴露并修复生成器缺陷：CRUD 测试示例值 `字段名-created/updated` 可能超过 `string(N)` 上限
  （channelType string(16) 越界导致 500）；`sampleValue`/`sampleJsonValue` 新增
  `paddedSample()` 按 `EntityField.size()` 截断；新增单测
  `crudIntegrationTestSamplesRespectStringSize`（string(8) 边界），32 tests 全绿。
- 独立构建直接暴露旧 SNAPSHOT 污染：`~/.m2` 中 8 月 5 日的 starter-persistence POM 缺
  mybatis-plus 版本（消费者传入失败）；重装最新 reactor 后解析正常——提示非 SNAPSHOT
  发布前必须先跑 consumer 通道刷新隔离仓库。

2026-08-09 P2 收口与 P1 可重复验证基线（`reset/0033-greenfield`，worktree 全量）
- P2 Create & Generate 四项退出门禁闭环（详见 §1）；`verify-initializer-consumer.sh`
  三通道（普通/postgres/CRUD 变体）与 `measure-ttcrud.sh`（124s/门禁 1800s）本地重跑通过：
  CRUD 全链路 create→get→update→list→delete→404 走真实 postgres:18.3-alpine，0 skipped。
- `verify-maven-consumers.sh` 本地重跑通过：reactor clean install 到隔离 repo 后，
  8 个 library 制品 sources/javadoc 伴随件齐全、18 个 consumer POM 无裸 `${revision}`、
  3 个制品 `spring-configuration-metadata.json` 存在、`maven-artifact-plugin:compare`
  可重复性通过、BOM 下 Maven 3.9.16 与 Maven 4.0.0-rc-6 双 golden consumer 均构建成功。
- `./mvnw clean verify` 全量（JDK 25 + Colima Docker）BUILD SUCCESS：241 tests /
  0 failure / 0 error / 0 skipped，`check-surefire-results.sh` 通过；offstate 最小应用
  `OffStateApplicationTest` 0 skipped。
- 剩余 P1 发布动作依赖 GitHub Packages PAT + 签名密钥（release/GPG）等仓库资产决策，
  形成真实 non-SNAPSHOT 发布记录后关闭 P1；P0 剩余分支保护（private + 免费版限制）与
  仓库可见性决策待定。

2026-08-04(续) CI 首次跑绿 + 基线合入 dev + 许可证决策。
- CI run 30904716377（`ubuntu-24.04` 原生 Docker）`completed=success`：全量 reactor verify +
  `check-surefire-results.sh` 0-skipped + Maven 3.9/4 consumer + CycloneDX SBOM。修复 2 个 Instant flaky
  （纳秒 vs PG `timestamptz` 微秒，commit `397b021`）后首次跑绿——关闭「CI 首次成功」P0 门。
- PR #2（`codex/scaffold-modern-baseline` → `dev`）已 merge（`5480457`）；scaffold 现代化基线
  （ADR-0029 P0 / 授权 S0 / Maven 4 RC6 / ADR-0033 Greenfield）集成进 `dev`。
- 许可证决策：**暂不开源**（私有/专有）。LICENSE P0 项按「不需要 OSS 许可」处理；未来若开源或对外发布，再定商业/开源许可。
- 分支保护：private 仓库 + GitHub 免费版**无法启用分支保护**（HTTP 403，需 GitHub Pro 或转 public）。CI 仍跑在
  `pull_request` / `push(dev,main)`，靠「绿了再合」软约束；硬性 gate 待仓库可见性/计费决策。

2026-08-04 关闭 P0 的 Maven 4 Wrapper 阻断。RC6 现已正式同步到 Maven Central
（`https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-6/`，HTTP 200）。此前
`.mvn/wrapper/maven-wrapper.properties` 的 `distributionSha256Sum` 取自 Apache 临时候选目录那份发行包，
与 Central 正式发布版字节不同，导致 `./mvnw` 报 "Failed to validate Maven distribution SHA-256"。已用
Central 正式发布版的 SHA-256 更新 wrapper（`e7a17cac…`，并经官方 `.sha512` 兄弟文件 `8167e73d…` 交叉
校验证明为 Apache 真品）。更新后从干净缓存首次跑通：`./mvnw --version` 显示 Maven 4.0.0-rc-6；
`./mvnw clean verify`（JDK 25、Docker/Colima 在线）15 模块 BUILD SUCCESS，326 tests / 0 failure / 0 error /
起初一次跑得 **105 skipped**（根因是 wrapper SHA 过期，已修）。0-skipped 门禁同日关闭：用 `testing.md` §4
既有的 Colima 配方（`DOCKER_HOST` 指向 Colima socket + `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`）
跑全量 `./mvnw clean verify`，达成 **326 tests / 0 failure / 0 error / 0 skipped**，`scripts/check-surefire-results.sh`
通过。先前 105-skip 的真因是 Testcontainers 的 Ryuk 容器无法 bind-mount 裸 Colima socket 路径（virtiofs
`operation not supported`），导致 `DockerAvailableDetector` 误判无 Docker、`disabledWithoutDocker` 跳过全部 105 个
集成测试；单独设 `DOCKER_HOST` 不够，必须配合 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` 让 Ryuk 把 socket 挂到
`/var/run/docker.sock`（Colima 映射）。同日 `scripts/verify-maven-consumers.sh` 通过：15 个 consumer POM 无裸
`${revision}`、3 个制品含配置元数据、`artifact:compare` 可重复性、Maven 3.9+ 与 Maven 4 golden consumer 均能
经 BOM 构建。至此 ADR-0026 §验收方式 全部本地满足。后续（见下条）：CI 已首次跑绿、PR #2 合入 dev、
许可证决策为暂不开源；P0 仅剩分支保护（受 private + GitHub 免费版限制）与秘密扫描。

2026-07-31 静态核对当前工作树：`ainer-starter-web` 已使用
`spring-boot-starter-webmvc` 与 `spring-boot-starter-webmvc-test`，ADR-0029 T0 第 1 项的
Web Starter 实现范围已经落地。当前工作树的最终 Maven 4 验证仍未完成：Wrapper 配置的
Maven 4.0.0-rc-6 持久下载地址仍返回 404，因此无法从干净缓存完成 `./mvnw --version` 与
`./mvnw clean verify`。该阻断不回退已经实现的 POM 修改，但在官方发行包可用、完整 Reactor 与
consumer 门禁重跑通过前，当前工作树不能形成发布候选。

2026-07-30 已用 JDK 25、Maven 4.0.0-rc-6 与
`postgres:18.3-alpine` Testcontainers 完成 MyBatis-Plus Boot 4 persistence starter 原型。
原型验证 `BaseMapper` insert/select、PostgreSQL `DEFAULT uuidv7()` 生成键回填与 UUID version
7、自定义 XML 共存、显式 tenant 条件、分页 total/记录，以及自动配置中的全局
`IdType.AUTO`、UUID TypeHandler 和 `maxLimit=100`。随后在同一 JDK 25 / Maven 4.0.0-rc-6 /
Colima 环境执行完整 `clean verify`：14 个 Reactor project、303 项测试、0 failure、0 error、
0 skipped，既有复杂 XML 与真实 PostgreSQL 路径全部回归。隔离发布门禁也已通过：Maven 4
producer/Consumer POM 与可重复制品检查成功，Maven 3.9.16 和 Maven 4 外部 golden consumer
均能导入 BOM、消费 persistence starter 并编译 `BaseMapper<?>` 引用。该结果接受 ADR-0028
的受限基础设施增强，不代表整个项目已达到发布候选状态。

2026-07-30 隔离评估过把基线提升到官方 OpenJDK 27 EA Build 32。Maven 4 可以在该 JDK 上完成
`validate` 并开始以 `--release 27` 编译，但 ArchUnit 1.4.2 无法读取 class major 71，架构测试
因此不能导入被测类；Spring Boot 4.1 的官方兼容范围同时仍止于 Java 26。项目没有关闭架构规则，
也没有引入 ArchUnit 快照或私有补丁，而是按 ADR-0027 保持 JDK 25 LTS、`--release 25` 和
Enforcer `[25,26)`。JDK 27 只保留为 GA 与依赖生态就绪后的升级候选。

2026-07-30 使用 JDK 25 与已校验的 Apache Maven 4.0.0-rc-6 发行包预置 Wrapper 缓存，在当前
工作区完成 `./mvnw clean verify`：14 个 Reactor 模块成功；Surefire 共发现 300 个测试，
0 failure、0 error，其中 104 个 Testcontainers 测试因当前机器没有 Docker 而跳过。
`scripts/verify-maven-consumers.sh` 的隔离 `install`、两次制品比较和独立 Maven 4/Maven 3.9+
consumer 均成功；两类 consumer 都能导入 `ainer-dependencies` BOM 并消费 Starter。标准
Consumer POM 中的 `${revision}` 均有当前安装版本属性可解析；这里不包括 Maven 4 额外保存的
`*-build.pom`。关闭 Flatten Maven Plugin 后，标准 Consumer POM 与原方案逐字节一致；额外启用
`maven.consumer.pom.flatten=true` 只会显著展开 POM，因此当前固定为 `false`。使用 Maven 3.9.16
执行生产者 `install` 会在 parentless BOM 的 `validate` 阶段失败，并在写入任何 `dev.ainer`
制品前停止。这些结果证明迁移实现可行，但不等于发布候选的 0-skipped 门禁，也不证明正式 CI
已建立。

上述验证使用的发行包来自 Apache 临时候选目录并已完成官方摘要校验。仓库已生成 Maven Wrapper
3.3.4，并配置
Maven Central 持久地址与发行包 SHA-256；但 2026-07-30 当前执行 `./mvnw --version` 仍因
Central 尚未同步该发行包而下载失败。ADR-0026 禁止回退到可能被删除的候选目录；只有官方端点
可用且 `./mvnw --version`、`./mvnw clean verify` 实际通过后，Maven 4 构建切换才能标记为完整
实施。

2026-07-28/29 的 M4.8B + M4.8C 候选在 macOS Colima、Testcontainers 2.0.5 与
`postgres:18.3-alpine` 环境完成完整 `mvn clean test`：14 个 Reactor 模块成功，全部测试
实际执行通过，0 failure、0 error、0 skipped。真实 PostgreSQL 从空库执行全部 migration，
覆盖：单租户用户 `GET /api/me/tenants`、SERVICE 403、多租户选择非默认 tenant 后 token
`tenant_id`/`roles` 来自实时 membership、OWNER 转移角色原子交换 + 审计 + 双方 access event、
非 OWNER 发起被拒、非 ADMIN 目标被拒、每 tenant 最多一个未完成转移、发起者取消后可再次发起、
并发接受只能成功一次（4 线程）、过期转移不可接受但可取消、HTTP 端到端 ownership-transfer、
OWNER 丢失恢复双 SERVICE request/approve + 同一 SERVICE 拒绝 + 非 ADMIN 目标拒绝。

2026-07-27 在 `https://ainer-dev.xiaoqu99.com` 完成首次真实公网联合验收。Authorization Server
release 为 `3f9420a4425f11e78feace776fe0b15853a0b884`，Ainer Studio/Admin release 为
`d13fe026cd5422f85f03c443e09f825c05e114a1`；systemd 与独立 PostgreSQL 18.3 均在线，空库
实际执行 16 份 migration，公开 discovery issuer 与规范 origin 一致。无网络拦截的 Chromium
实际完成表单登录、Authorization Code + PKCE、成员读取/添加、MEMBER → ADMIN → MEMBER、软移除、
当前 access token 撤销、RP-Initiated Logout 和退出后重新访问要求登录。公网延迟暴露的 Studio
退出导航/路由守卫竞态已由 `d13fe02` 修复并复验通过；fixture 运行开关已关闭，密码不在 Java
EnvironmentFile。

2026-07-27 的 M6 品牌登录候选在 macOS Colima、Testcontainers 2.0.5 与
`postgres:18.3-alpine` 环境完成 `mvn clean test`：14 个 Reactor 模块成功，71 个测试套件、
281 个测试全部实际执行通过，0 failure、0 error、0 skipped。真实 Chromium 直接访问候选
Authorization Server 的 `/login` 与 `/login?error`，并以同一服务端模板/CSS 检查合同规定的
429/503 视觉状态；normal、credential-error、rate-limited、service-unavailable 四种状态均完成
1440×900 和 390×844 截图复核，桌面/移动共 8 组 axe-core 4.12.1 扫描均为 0 violation。
服务端测试同时覆盖 CSRF、SavedRequest/PKCE、通用凭据错误、一次性 503、HTML 429、
`Retry-After`、no-store 与既有 Passkey/WebAuthn ceremony。Studio 合同和 Tokens 的 SHA-256
分别保持 `e8e50c266957c7fe14af4b4e30508dd6fe52f43c12029261d8a44e5d51ce2786` 与
`2a8eeed8d598ebc647163662a7de8f7bb0d0ce2e3a171e2392e638ba75c095d8`。该候选尚未推送或部署，
不能替代现有 dev 公网验收。

2026-07-27 部署工具通过 `bash -n`、隔离路径/精确代理静态门禁和 `git diff --check`。Java
`mvn test` 的 14 模块构建成功，但执行机当时没有 Docker，Authorization Server 的 30 个
Testcontainers 测试按既有 `disabledWithoutDocker` 策略跳过；该次运行不替代下述 0-skipped
基线，也不能作为公网 dev 部署验证结果。首次上线仍必须用服务器真实 PostgreSQL migration 和远程
Chromium 联合验收关闭门禁。

2026-07-26 在 macOS Colima、Testcontainers 2.0.5 与 `postgres:18.3-alpine` 环境执行完整
`mvn test`：14 个 Reactor 模块成功，67 个测试套件、271 个测试全部实际执行通过，
0 failure、0 error、0 skipped。

本轮 Ainer Admin 验证使用固定 `ainer-admin-dev` public client、同一 HTTP cookie session 与
`postgres:18.3-alpine` 从空库执行 Authorization Server 16 份 migration。端到端覆盖
Authorization Code + PKCE S256、无 Refresh Token、default tenant/OWNER claims、成员 GET、添加
已有用户、MEMBER → ADMIN → MEMBER、软移除与 `[ADDED, ROLE_CHANGED, ROLE_CHANGED, REMOVED]`
审计；自助撤销后旧 access token 被 active gate 返回 401，ID token 仍可完成
`/connect/logout` 并精确返回 `/ainer-admin/auth/logged-out`。全量回归还把旧成员 API 集成测试
从“只签名 JWT”改为持久化真实 active authorization，避免测试绕过新的在线活性边界。
本次已把 `dev@a22e121` 的 M4.8A 合入 Ainer Admin 分支。Java、POM、`application.yaml`、Admin
OpenAPI 与 M4.8A migration 自动合并且没有 migration 版本碰撞；7 个冲突全部位于 README、
Changelog、ADR 索引和状态文档，并已保留双方内容。安全链顺序保持为协议端点、内部控制面、
M4.8A 激活、Admin 成员/revoke active gate、指标和默认登录；M4.8A tenantless SERVICE Token
夹具与 Admin active authorization 夹具彼此隔离。融合回归还修正了 M4.8A 新用户激活测试把
`IdentityAccount.roles()` 误写为领域角色 `OWNER` 的断言，使其与既有 Spring authority
`ROLE_OWNER` 契约一致；对外 Token `roles` claim 仍为 `OWNER`。
`docs/README.md` 继续只是目录门面，`docs/00-overview.md` 是唯一权威入口并已纳入
`ainer-admin-integration.md`。严格 OpenAPI 校验和 TypeScript SDK 生成成功；
`ainer-admin-v1.yaml` SHA-256 保持
`1269a0e325f645ab9371a7783635e0a7cdfe1bfad4cd11b56bc6ade5f2468056`。

本轮 M4.7 新增 Identity 管理面验证：Identity 模块从空库执行 6 份 migration，真实 PostgreSQL
覆盖成员列表、按 username/subjectId 加入、角色变更、软移除、DISABLED 重激活、OWNER 保护与每次
写入的 operation/reason/request ID 审计。Authorization Server 从空库执行 13 份 migration，
随机端口 HTTP 使用实际 RSA Bearer JWT 覆盖匿名 401、缺 scope/SERVICE/MEMBER/跨 tenant 403，以及
加入、列表、改角色、移除和 3 条审计落库；同时证明成员 API 只使用 Identity 权威数据库。
bootstrap 用例证明首次创建、重复执行不覆盖密码、部分 tenant/username 占用失败关闭。

此前 M4.8A 预配申请验证：Identity 模块从空库执行 7 份 migration，真实 PostgreSQL 覆盖规范化
请求、相同摘要幂等重放、同幂等键下 tenant name/change reference 变化冲突、tenant code 双线程并发
预留只成功一次、过期释放、ACTIVE 用户复用、LOCKED 用户拒绝、与 bootstrap 共享冲突门禁、核心
tenant/user/membership 零污染和 request/phase audit。Authorization Server 从空库执行 14 份
migration，随机端口 HTTP 使用实际 RSA Bearer JWT 覆盖匿名 401、缺 header 400、缺成对 scope、
tenant-bound SERVICE、USER、白名单外 operator 的 403，以及 POST/GET、安全投影、no-store、
幂等冲突和审计落库。配置与 bootstrap 单元测试覆盖空 operator、TTL 边界、弱 secret 和策略不匹配
既有 client 的启动失败。

本轮激活核心增量验证：本机 PostgreSQL 18.4 随机 schema 经 Flyway 实际执行 Identity 全部 8 份
migration，跑通申请、AES-GCM 通知解密、错误 secret 次数持久化、成功原子激活和回放拒绝，结束后
schema 已清理；同一批 migration 还在单事务临时 schema 中完整执行并回滚。新增不依赖 Docker 的
4 个测试覆盖 AES-GCM round-trip、tamper、未知 key、旧 key rotation 读取与 provider 失败延迟重试；
配置测试覆盖 activation TTL/次数/key ring 失败关闭。完整 Identity PostgreSQL 用例又加入锁定、
过期、已有用户 subject 绑定、默认 tenant 保留、核心写入失败回滚和密文不含 secret 断言。

本轮通知 transport 增量已实现独立 tenantless relay client bootstrap、OAuth2 Client Credentials、
HTTPS gateway publisher、稳定 `Idempotency-Key`、调度领取、失败分类和 pending/failed/exhausted/
cancelled/oldest-ready 指标。HTTP 合约测试证明 Bearer、版本化 envelope、新用户激活材料和已有用户
无 secret 投影；配置测试证明普通 HTTP、带 query 的 URI 和非法重试边界失败关闭。网关 2xx 后或
请求取消时，Identity 会销毁 `protected_payload` 的可解密内容；`PUBLISHED` 只证明网关持久接收，
不代表邮件、短信或站内信最终送达。

本轮平台控制增量增加成对 write scope 的显式 cancellation，以及各自 read scope 的 tenant/user
安全分页。非 Docker 单元测试覆盖 tenantless SERVICE、operator 白名单、scope 拆分、分页边界和
取消指标；PostgreSQL 18.4 隔离 schema 重放全部 8 份 Identity migration，并实测 request/grant/
outbox 同时 `CANCELLED`、payload 销毁和取消审计。Testcontainers 用例还覆盖重复取消不重复审计、
新用户 grant 缺失时整笔回滚、已有用户无 grant、稳定排序/total 和 HTTP 响应不含凭据数据。

本轮终态回执增量增加独立 gateway client bootstrap、tenantless SERVICE + 专用 scope + 精确白名单
安全链、`DELIVERED|FAILED` 最小模型、单 notification 终态与 gateway event 幂等、抢先回执状态
冲突以及首次终态 Counter。10 个不依赖 Docker 的新增测试覆盖参数/未来时间、回放/冲突、Controller
安全投影、配置失败关闭和 bootstrap 策略。本机 PostgreSQL 18.4 隔离 schema 从空库重放全部 9 份
Identity migration，验证回执 UUIDv7、合法终态写入、重复 notification 唯一冲突和 `FAILED`
空失败码拒绝；该 smoke 实际发现并修正了 SQL 三值逻辑下 NULL 逃逸 check 的问题，schema 已清理。
随机端口 OAuth2/Bearer 与真实事务 Testcontainers 用例已写入并通过 test compilation，但当前无
Docker，尚未实际执行，不能计入 0-skipped 发布验证。

当前机器未运行 Docker。本轮干净执行 `mvn clean test` 时 14 个 Reactor 模块全部成功，
Surefire 共发现 61 个测试套件、256 个测试；其中 172 个实际执行并通过，0 failure、0 error，
84 个 Testcontainers 测试因 `disabledWithoutDocker=true` 跳过。因此上述本机 PostgreSQL smoke
是真实增量验证，但不是新的完整 0-skipped 发布快照。上方 221-test 结果仍是最近一次完整不跳过
基线，合并/发布前必须在 Colima/Testcontainers 可用环境重跑全量并更新数字。

本轮安全收口还验证：Passkey 恢复/enrollment 对目标 ACTIVE default membership 的跨 tenant guard 与
数据库复合外键；登录限流在 context path 下对 WebAuthn options 返回统一 429、
`Retry-After`/no-store 并记录 allow/deny；step-up 真实 HTTP 覆盖匿名 401、USER 成功、SERVICE/
缺因子/旧时间/越界未来时间 403。全量测试还暴露并修正了 AI 测试 JWT 缺必填 `actor_type` 的旧夹具。

累计 Phase D resource server step-up 验证：`RecentStrongAuthenticationFilter` 单元测试覆盖
`amr` 含必需因子且 `auth_time` 新鲜放行、密码 Token 缺 `mfa` 返回 403（错误体含特定错误码）、
`auth_time` 过期/缺失拒绝、缺 `amr` 拒绝、非 JWT 认证拒绝、非受保护路径不节流，以及 `StepUp`
配置校验拒绝空规则/空 `required-amr`/超 24 小时 `max-auth-age`。这是 resource server 第一次消费
Authorization Server 在 Phase A 签发的 `amr`/`auth_time`。filter 默认关闭，与在线校验 filter 同锚点。

累计 Phase C 限速与受控 enrollment 验证：限速器单元测试覆盖窗口内放行、超额拒绝、不同 key
独立计数、跨窗口复位与 `Retry-After` 取整；受控首次 enrollment 在真实 PostgreSQL 上验证
`require-invite` 模式——无授权的首枚 Passkey 登记被拒（`ENROLLMENT_GRANT_REQUIRED`），操作员建立
授权后首登成功且授权同事务置 `CONSUMED`，已有 ACTIVE Passkey 的 replacement 不受影响。限速明确为
node-local（全仓无 Redis），多实例需共享存储留待后续。限速 filter 的端到端 HTTP 429 已完成；
enrollment 服务控制面与真实登记拒绝路径已由 PostgreSQL 集成测试覆盖。

累计 Phase B Passkey 恢复验证：恢复码自助流程在真实 HTTP 会话与 PostgreSQL 上跑通——
真实 Passkey 登记后签发 8 枚高熵一次性恢复码（明文仅返回一次，库内只存 bcrypt 哈希），
密码登录本人用一枚恢复码赎回后，该账号全部 ACTIVE Passkey 被吊销并写 `SELF_RECOVERY`
安全操作审计，用户可重新 bootstrap。管理员双人恢复在 service 层用真实事务验证：申请者建立
`REQUESTED`，同服务批准被拒（`RECOVERY_APPROVER_MUST_DIFFER`），不同服务批准成功、吊销目标
全部 Passkey，`(operation_id, phase)` 偏唯一审计为 `[REQUESTED, EXECUTED]`，重复批准被拒。
恢复码失败尝试按 subject 累计并锁定；最后凭证保护在恢复上下文中被安全越过（不破坏普通自助
删除的最后凭证保护）。通知（含联系字段与可达通道）仍为已知缺口，未在本切片交付。

累计 Phase A Passkey 真实签名 ceremony 端到端验证：用 webauthn4j 虚拟 authenticator
驱动 `/webauthn/register`（真实 attestation）与 `/login/webauthn`（真实 assertion）闭环，
真实走通 Spring Security 7.1 的 `Webauthn4JRelyingPartyOperations` 签名校验代码路径；
Passkey 用户完成授权码流程后，access token 携带 `amr=pwd,mfa,pop`、`auth_time`、稳定
`sub`（subjectId UUID）、`tenant_id` 与 `roles`。这组测试同时揭露并修复了三个此前被合成
CredentialRecord 测试掩盖的真实缺陷：OAuth2 授权记录无法反序列化 `WebAuthnAuthentication`
主体（注册 `WebauthnJacksonModule`）、Passkey 用户 token 因 customizer 不认 WebAuthn
principal 而缺 Ainer claims（customizer 改为按 username 解析 `AinerUserDetails`），以及
凭证管理端点 `/webauthn/register/**` 因协议 filter 在授权 filter 之前短路而未被条件 MFA
门禁保护（新增 `AinerPasskeyCredentialManagementGateFilter`，锚定 `CsrfFilter` 之后、协议
filter 之前）。HTTP 层门禁测试覆盖：已登记账号在缺因子时 `/webauthn/register/options` 与
`DELETE /webauthn/register/{id}` 均 302 到 `/login`，凭证不被新增或删除；首次 bootstrap
（未登记账号）不受影响。本轮尚未覆盖真实设备/浏览器兼容矩阵。

累计 OAuth Client 生命周期验证：创建
managed client 后只保存 password hash，一次性 secret 可正常换取 tenant-bound JWT；未授权 scope
返回稳定 422，tenant-bound operator 返回 403。蓝绿轮换期间新旧 ID 并行可用，显式退役后旧
secret 换 Token 返回 401、历史 Token introspection inactive，而 Spring 官方 JDBC authorization
仍可重建历史记录。配置测试覆盖空 operator、平台/`.all` scope、过长 access token 和弱随机
secret 的启动失败；operator bootstrap 只创建无 tenant、`oauth.clients.manage`、一分钟 Token。
创建、轮换、退役审计表没有 secret 字段。

本轮新增 PKCE 验证：测试专用 public client 通过真实表单登录、cookie/CSRF 和 S256 challenge
取得 authorization code，并用正确 verifier 交换出包含人员 `sub`、`tenant_id`、`roles` 的
access token 和 OIDC ID token；响应不含 refresh token。authorization code 重放、错误 verifier、
缺失/`plain` challenge 均失败，未注册 redirect URI 不发生外部跳转。真实 JDBC 往返暴露并修复
了 Jackson 3 拒绝 Ainer 人员 principal 的问题；修复只增加精确类型白名单，并证明授权记录不含
密码或 password 字段。

本轮新增 Passkey 验证：Authorization Server 从空库执行七份 migration，创建 Spring 官方
WebAuthn 协议表和 Ainer 生命周期/审计表。配置门禁拒绝 IP 型 RP ID、越界 Origin、普通 HTTP、
重复 Origin、路径和过长 timeout；真实 HTTP registration options 强制 resident credential 与
`userVerification=required`。无凭证账号仍可用密码完成 PKCE，Token 含 `amr=pwd` 和
`auth_time`；登记合成 credential 后，仅密码不能取得 authorization code。JDBC 门禁验证登记、
计数器/last-used 更新、replacement、软撤销、审计和协议记录保留；并发撤销两个 ACTIVE
credential 时只允许一个成功，最后一个保持 ACTIVE。后续虚拟 authenticator 签名 ceremony 已完成，
但主流真实设备兼容矩阵仍未完成，因此不能把自动化验证表述为生产兼容性认证。

本轮新增指标安全验证：Resource Server 真实 HTTP 测试覆盖无 Token 401、USER/tenant-bound SERVICE/缺 scope 403、tenantless SERVICE 200，并使用自定义 management base path 证明路径配置不会绕过授权；路径 matcher 还覆盖 context path、尾斜杠和编码路径。业务 Resource Server 显式关闭时，真实 Prometheus endpoint 仍拒绝匿名并且不返回 JVM/process 指标。metrics bootstrap 测试证明只创建 Client Credentials、无 tenant、只有 `platform.metrics.read`、一分钟 Token、无 introspection 标记，重复运行不覆盖且弱 secret 失败关闭。Authorization Server 的真实 PostgreSQL 协议测试已实际验证专用/tenant-bound metrics Token 与 exporter 的 401/403/200，并同时覆盖 Client Credentials、OIDC discovery、专用 introspection、RFC 7009 与 Identity revocation epoch。

同日使用本机真实 PostgreSQL 18.4 从空库执行 Identity 四份、Workspace 八份全量 migration。除 M4.1 的 outbox 领取/撤销验证外，本轮实际执行了耗尽原事件双人重放事务、REVOKED OWNER 提升新 OWNER 事务、安全操作审计约束，以及授权审计归档 CTE、热冷统一查询和导出审计。原事件内容保持不变，旧 OWNER 保持 REVOKED，归档后热表 0/归档表 3；两个一次性数据库均已删除。loopback HTTP 测试实际验证 Client Credentials Token 获取/缓存和 Bearer 事件发布。

M4.3 另使用本机 PostgreSQL 18.4 从空库执行 Authorization Server 五份 migration 并实际启动发行物。协议 smoke 证明普通 client introspection 返回 401、专用 client 对新 Token 返回 `active=true`、RFC 7009 revocation 返回 200 且随后 `active=false`。真实 JDBC 往返同时暴露并修复了 Boot 4/Jackson 3 对 JDK 私有不可变 claim 集合的反序列化拒绝。对 5,000 条合成 access event 的 revocation epoch 查询使用 `idx_ainer_identity_access_event_subject` Index Only Scan，实测执行约 0.036 ms；旧/等于 epoch 的 Token inactive，事件后的 Token active，membership 禁用后当前身份 inactive。一次性数据库和 RSA 测试密钥目录均已删除。

该结果证明当前源码基线可构建，且全部自动化 PostgreSQL 集成组已在本地 Docker-compatible runtime 中实际执行；它仍不是独立发布候选环境的验证结果，也不证明生产容量、备份恢复或高可用。

## 4. 已知缺口

### 访问控制

- 通用混合细粒度授权已按 ADR-0037 接受为 post-Greenfield Workspace 基线，但 `0.1` 的支持面仍有
  明确边界：`@AinerAuthorize` 只有 endpoint 粗门禁，当前 synthetic target 固定
  `resourceType=request`；`AuthorizationTargetResolver`、DecisionObligationExecutor、RFC 9470 challenge
  和方法级 AOP 未实现。高价值写与资源 ownership 必须继续在 application service 显式授权；远端
  不可变制品、Ainer Admin、外部产品关系/字段投影和生产撤权 SLA 尚未验收；
- 组织与员工目录当前只有 ADR-0032 和详细方案；`ainer-module-organization`、OrgUnit、
  WorkforceEngagement、Position/Assignment、SubjectSetBinding、管理 API 和 XQ 岗位纵向切片均未
  实现。普通 tenant 成员角色变更/移除还没有完整写入 access-event outbox，Identity 已定义的
  role-changed 事件与 Workspace consumer 合同也不兼容；在修复并验证 Token 失效、Workspace 撤销、
  workforce-derived grant 撤销三条独立语义前，不能承诺调岗/离职即时失权；当前 subject-scoped
  access event 也不能表达 tenant-wide disable，需补 tenant epoch/event 或等价在线门禁；
- 选择性在线校验只覆盖配置的高风险 API；普通低风险自包含 JWT 仍有自然到期窗口；
- Authorization Server 已成为高风险 API 在线依赖；受保护 exporter 与独立 metrics/introspection 凭据创建基线已有代码，但尚未完成生产高可用、容量、旧凭据退役、真实 Prometheus、dashboard 与告警；
- 重放与 OWNER 恢复已做服务 `sub` 分离，但生产 IAM 仍需证明凭据由不同人员/职责保管；
- 授权审计归档仍位于同一数据库，没有 WORM、数字签名、法律保留和最终删除策略；
- SIEM 只有默认关闭的拉取 API，没有部署外部消费者、不可变副本或告警路由；
- Directory/relay/consumer 与 M4.2 控制面默认关闭，尚未在真实多节点环境完成容量、故障注入和滚动 30 天撤销 SLO 验证。

### Identity 与 OAuth

- tenant-bound Client Credentials 已有生命周期控制面，但 browser/OIDC、平台级
  metrics/introspection/operator、`.all` 与既有 bootstrap client 尚未纳管，也没有列表分页、审计
  导出、双人审批或 UI；
- Authorization Code + PKCE 与 Passkey 条件门禁、虚拟 authenticator 签名 ceremony、恢复、
  受控 enrollment 和 Resource Server step-up 已有自动化验证，但生产 browser/OIDC client 控制面、
  恢复通知、真实设备矩阵、共享限流、多节点会话和签名密钥轮换未完成；品牌登录合同 1.0.0
  明确不提供可见 Passkey 动作，因此需要人员 Passkey 登录的部署仍需等待 Studio 新合同；
- 平台级 tenant/user 控制面已有默认关闭的预配申请/查询、一次性激活核心、加密 notification
  outbox、OAuth2/HTTPS 通知网关 relay、已有用户本人接受、安全分页、显式取消与 provider-neutral
  终态回执接收；真实外部通知网关/供应商、供应商回执映射和最终送达验证、禁用/恢复、tenant
  ownership transfer 和成员管理 UI 尚未完成；
- tenant 成员 API 已随 S8 删除；Ainer Admin 只保留当前会话自助撤销端点，其撤销端点已强制
  逐请求 online active gate，数据库故障返回 503 并失败关闭；不能把业务 Server 的默认
  step-up 规则误认为覆盖该端点；
- Ainer Admin 同源代理已有契约但尚未在选定生产 ingress 上完成 HTTPS、Cookie、重定向和缓存
  验收；`ainer-admin-dev` 与开发身份不能替代生产 browser client 生命周期和正式开户。

### AI 平台

- 限流仍是单进程基线，未形成集群级一致性；
- provider 凭据托管、指标、trace、输出策略、评测、RAG 与 Agent runtime 尚未完成；
- 通用 Run / Artifact 与 Knowledge 仍只有 Proposed 数据模型；ADR-0023 已提前落下受治理
  Task/Run/Result/Feedback migration 与应用代码，但 ADR 仍为 Proposed，且事务、tenant/actor
  授权、UUIDv7、invocation linkage、Context 实际输入和正文数据治理尚未通过验收，因此不得
  描述为已完成平台能力；
- 供应商兼容面仅覆盖当前 OpenAI-compatible 最小协议。

### 工程与运营

- PostgreSQL Native-First 目标已由 ADR-0020 和数据库规范 1.2 确立。Workspace、AI Runtime 与
  Authorization Server 的持久化身份已于 2026-08-13 全域迁移到应用层 `Uuidv7.generate()`（持久化路径
  零 `UUID.randomUUID()`，见 §3），identity 模块沿用 DB 端 `uuidv7()`；Greenfield 已移除 Workspace/AI
  的 `tenant_id` 列；统一 `RETURNING`、全域应用层生成与 1.0 clean baseline 的剩余收敛仍属后续；
- MyBatis-Plus Boot 4 starter 的真实 PostgreSQL 原型、全量 Reactor、既有复杂 XML 与
  Maven 3/Maven 4 外部 consumer 回归已经通过；后续风险转为版本升级回归、规则误用和正式 CI
  尚未固化这些门禁；
- Maven 4.0.0-rc-6 仍是 preview；RC6 已于 2026-08-04 确认正式同步到 Maven Central，wrapper SHA 已
  修正为 Central 正式发布版校验值，`./mvnw --version` 与 `./mvnw clean verify` 已从干净缓存跑通
  （15 模块 BUILD SUCCESS）。用 `testing.md` §4 Colima 配方跑全量 verify 已达成 `0 skipped`
  （326/0/0/0，见 §3 2026-08-04 记录）；`scripts/verify-maven-consumers.sh` 也已通过（consumer POM、
  配置元数据、可重复性、M3.9+/M4 consumer）。ADR-0026 §验收方式 已本地满足；CI 已首次跑绿（见 §3
  2026-08-04(续)）、PR #2 合入 dev、许可证决策为暂不开源（私有/专有）；P0 仅剩分支保护（private + GitHub
  免费版无法启用，待可见性/计费决策）与秘密扫描（pre-public 前最有价值）；
- 已增加只读权限的候选 GitHub Actions 质量门禁，编排 JDK 25、Maven 4、Docker、
  PostgreSQL/Testcontainers `skipped=0`、Maven 3/4 consumer 与短期 CycloneDX SBOM；RC6 已上 Central、
  wrapper 已修、本地 `./mvnw clean verify` 已达成 `0 skipped`、consumer 门禁已通过（2026-08-04），工作流已
  首次跑绿（run 30904716377，ubuntu 原生 Docker）；但 private + GitHub 免费版无法启用分支保护/必需检查，
  目前靠「绿了再合」软约束，硬性 gate 待仓库可见性/计费决策，故尚未称为正式 CI；
  `0.1.0-rc.1` 已完成一次正式 key 的签名 deploy，但因源码/tag 不一致、无 GitHub Release、无完整
  远端消费与 provenance 证据而撤回。当前 workflow 进一步要求 annotated tag/source、目标版本不存在、
  passphrase-protected key、远端空仓 consumers、107/107 读回验签、签名 SBOM/checksum/provenance 和
  immutable GitHub Release。新流程已由 `v0.1.0-rc.2` 的 run `31666957663` 完整实跑，P1 合格受控
  RC 门禁已关闭；G2 仍等待真实产品的 migration replay、升级与回滚；
- Maven 4 当前 source model 仍会对 reactor 内部 BOM import 输出
  `BOM imports from within reactor should be avoided` 告警。ADR-0026 已明确批准当前 parentless BOM
  结构并把 POM 4.1/model 重构留给后续独立原型；`rc.2` 以 Maven 4 preview 风险、Maven 3/4 空仓
  consumer 和可重复构建门禁将其作为已知例外保留，不把告警宣称为已解决；
- 已有 `.github/CODEOWNERS`，但 private 免费仓库仍缺受保护分支/必需审查的远端强制执行；
- 没有生产备份恢复、容量测试、正式错误预算/告警路由和灾难恢复演练；
- 没有稳定版兼容政策；许可证决策为暂不开源（私有/专有），未来若开源或对外发布再定商业/开源许可；付费产品交付系统未建立；
- Testcontainers 仍使用 `disabledWithoutDocker`；本机 Colima 与候选 CI 均已完整执行，候选 CI 已用
  `scripts/check-surefire-results.sh` 明确拒绝任何 skipped 测试，但该门禁尚未纳入受保护分支的
  必需检查。

### 脚手架产品化评估

2026-07-30 的本地复审形成以下工程判断。百分比只用于确定投入顺序，不是发布承诺或质量度量：

- Ainer 作为安全、Identity、Workspace 与 AI 原生平台内核，成熟度约为 `65%–70%`；
- Ainer 作为可由外部项目直接消费和生成新项目的通用脚手架，成熟度约为 `35%–45%`；
- 当前不应复制 Ainer 源码创建产品仓库，应先完成制品发布、Project Initializer 和独立消费者门禁；
- `xq-platform-next` 应在 Scaffold Ready 与生成器门禁通过后作为首个外部消费者创建，不等待所有
  企业能力完成。

按
[`Ainer Boot 产品定位、竞品能力矩阵与路线图`](design/ainer-scaffold-design.md)
定义的全局产品化阶段，当前是 **G0 已冻结、G1 已关闭、G2 已关闭（`v0.1.0` 已发布并被真实
消费者消费）、进入 G3（产品核心闭环）**：P3 企业基座（文件/字典/配置/通知 + 缓存 + 授权）
全部进入 `0.1.0`；`xq-platform-next` 完成 `rc.2 → rc.3 → 0.1.0` 连续升级链与全部产品纵向
切片。G3 剩余：最小 Agent/Tool/Context/Evaluation 治理、组织目录（ADR-0032 需 Greenfield
重述，37 处 tenant 依赖）、Knowledge 两个语义切片（ADR-0034 基本兼容）。private 仓库分支
保护仍缺远端强制执行。

ADR-0029「JDK 25 / Boot 4 现代化基线」P0 进展（均经 `mvn 3.9.16 + -Denforcer.skip=true` 验证；正式
`./mvnw clean verify`、零跳过门禁与 Testcontainers 集成仍待 Maven 4 RC6 官方发行包恢复后执行）：

- P0-2 出站 HTTP：消除全部 4 处 `RestClient.create()/builder()` 反模式，统一注入 Boot 管理的
  `RestClient.Builder`；AI SSE 保留 JDK `HttpClient` 并显式注释例外。`@HttpExchange` + Service Client Group
  延后——现有 relay 各有独立 client-credentials token provider，套用 group/configurer 比当前显式注入更重。
- P0-3 配置即契约：公开制品统一生成 `spring-configuration-metadata.json` 并纳入消费者门禁；为原本无校验的
  配置类补齐 `@Validated` 声明式约束；**全部 22 个 `@ConfigurationProperties` 已改为构造器绑定不可变**
  （保留 getter 名零破坏调用点，构造器内处理默认值；含密钥/密码的用不可变**类**而非 record，故无
  `toString()` 泄露；`Pricing.validate()` 的字段改写已移入构造器）。
- P0-4 空安全基线：`@NullMarked` 已覆盖 ainer-core、ainer-spring、ainer-security、ainer-starter-web
  （8 包）并标注真实 `@Nullable`；@NullMarked 模块已声明 jspecify 直接依赖。**NullAway 强制未接入**：
  error-prone 2.50 + NullAway 0.12.7 在 Maven 3.9.16/compiler 3.14.0 + JDK 25 下无法工作：forked 编译拿不到
  `--add-exports` 致 error-prone 崩溃；in-process 下即便经 `MAVEN_OPTS` 确保 `--add-exports` 生效，
  `-Xplugin:ErrorProne` 插件仍不挂载、javac 拒绝 `-Xep` 标志——属 compiler 3.14.0/JDK 25 插件加载不兼容，
  非配置可调。多次尝试（模块局部 forked、根 forked、根 in-process + `.mvn/jvm.config`、`MAVEN_OPTS`）均失败，
  此前因 Maven 4 RC6 wrapper 阻断无法在正式工具链验证（该阻断已于 2026-08-04 修复，见 §3），故已还原配置
  保持构建绿色；NullAway 作为「CI 接入」项可在现已可用的 Maven 4 工具链上重新评估。
- P0-5 虚拟线程：`aiStreamExecutor` 已标记 `@Bean(defaultCandidate=false)`；**双模式压测矩阵
  已闭环**（`scripts/measure-virtual-threads.sh`，见 §3 2026-08-10 记录）：JDBC 分页场景双模式
  同级无回归，等待型场景（80ms×400 并发）虚拟线程 p50 减半、吞吐 +77%；**已按 ADR-0029 决策 5
  落地 Initializer 模板默认 `spring.threads.virtual.enabled=true`**，xq-platform-next 在默认
  虚拟线程下 4 tests 全绿。

## 5. 下一里程碑

`0.1` 主线按以下顺序推进：

1. `v0.1.0-rc.2` 已完成默认分支 CI、annotated tag、107/107 远端验签、空仓消费者、签名证据和
   immutable GitHub Release；该合格受控 RC 保留为升级/回滚的已发布起点，不覆盖或移动；
2. `v0.1.0-rc.3` 已完成 338/0/0/0、107/107 远端验签、Maven 3/4 与自带 Wrapper 的远端
   Initializer 消费、签名证据和 immutable GitHub Release；该版本是当前最终消费目标，不覆盖或移动；
3. ✅（2026-08-14）`xq-platform-next` 已删除 `0.1.0-SNAPSHOT` 与本地仓库依赖，固定从远端
   `rc.3` 消费，并以 `rc.2` 为起点完成升级、回滚、JWT、Workspace/资源授权、PostgreSQL
   migration replay、真实 HTTP 错误和客户端 SDK 的产品纵向切片（详见 §3）；
4. ✅（2026-08-14）`v0.1.0` 已发布（dev merge `ccd5097` + release run `31785695252` 全绿），
   走 rc.2/rc.3 同一完整发布门禁：annotated tag、签名 deploy、112/112 远端读回验签、
   空仓消费者、Initializer 三通道、SBOM/provenance 与 immutable Release（16 个签名证据资产）；
5. ✅（2026-08-14）`xq-platform-next` 完成 `rc.3 → 0.1.0` 真实升级验证（新隔离冷仓，0.1.0
   制品全部远端解析，14 tests / 0 skipped），首个连续升级链 `rc.2 → rc.3 → 0.1.0` 全绿；
   后续兼容性或发布链问题使用新的 `0.1.1`/`0.2.0` 修复，不移动 tag、不覆盖制品；
6. **G2 已关闭**。进入 G3（产品核心闭环：最小 Agent/Tool/Context/Evaluation 治理、组织目录
   Greenfield 重述（ADR-0032 有 37 处 tenant 依赖需取代性重述）、Knowledge 两个语义切片）；
   `python-learning-service` 保持版本化 BOM/Starter 接入，不绑定开发分支、不复制源码。

Identity、安全与运维纵深继续修复明确的 P0/P1/P3 风险，但方法级授权、组织目录、真实设备矩阵、
多实例容量、外部不可变审计和商业 entitlement 不再作为首个受控 RC 的无限前置。任何部署若实际
依赖这些能力，必须在对应产品发布门禁中单独补齐，不能借用 `0.1` 工程测试替代生产证据。

## 6. 更新规则

- 只写已经有代码、migration、测试或明确验证记录的完成项；
- 测试数量和版本变动后更新本页，不散落到长期规范；
- 缺口关闭时同时更新对应 ADR、专题文档和 Changelog；
- 发布版本形成后保留历史 Changelog，本页只保留最新状态。
