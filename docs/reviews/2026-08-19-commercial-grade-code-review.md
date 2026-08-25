# 商业级代码评审（2026-08-19）

> 评审动机：Ainer Boot 目标为商业级脚手架——付费客户将直接阅读源码。标准是「优秀」，不是「可用」。
> 方法：机械扫描 + 三路并行语义评审（最新三模块质量 / 安全关键链正确性 / 全模块 API 契约一致性）。
> 本文件是审计快照：记录发现、已修复项与遗留 follow-up。后续关闭记在「后续关闭」小节，
> 不改写 2026-08-19 原文。

## 已修复（本轮，全量 reactor 411/0/0/0）

### 安全（Critical/High）

| # | 发现 | 修复 |
|---|---|---|
| C1 | **starter 默认 fail-open**：`AinerSecurityDisabledAutoConfiguration` 的 `matchIfMissing=true`——客户漏配 `ainer.security.resource-server.enabled` 时全站匿名放行 | 删除 `matchIfMissing`；未配置时回落 Boot 默认链（全部需认证），永不落到 permissive 链 |
| H4 | **SubjectSet 跨工作区提权**：成员解析只按 positionId+subject 查询，集合声明的 workspaceId 从不与目录事实核验——「声明工作区 B + 工作区 A 的岗位」可授予 A 岗位任职者 B 的权限 | 解析 SQL 增加 `pa.workspace_id = #{workspaceId}` 过滤；新增跨工作区 DENY + 同工作区 MEMBER 对照回归测试 |
| H1 | **决策审计从未接线**：`AuthorizationDecisionAuditService.recordIfApplicable` 全仓零调用——「决策可审计」实际是空表 | `AinerRequestAuthorizationManager` 注入可选审计服务，决策后按 auditLevel 落审计（REQUIRES_NEW，DENY 审计在业务回滚后仍保留） |
| H2 | **PUBLIC 投影被误判拒绝**：`PublicProjection` 塞进 obligations 槽导致 `isGranted()` 恒 false，`PUBLIC_PROJECTION` 请求永远 403 | `isGranted()` 将 `PublicProjection` 识别为投影数据而非待执行义务 |
| H3 | **CHALLENGE 通道断裂**：Assurance 硬编码 NONE、CHALLENGE 被扁平化为 403——step-up filter 的强认证事实与授权上下文互不相通 | step-up filter 写 request attribute → manager 映射为 `Assurance.RECENT_STRONG`；interceptor 将 CHALLENGE 映射为 401（需重新强认证）而非 403 |

### 正确性（High）

| # | 发现 | 修复 |
|---|---|---|
| H-1 | **org CAS 结果被静默丢弃**：suspend/terminate/closeUnitAssignment 的乐观锁 UPDATE 命中 0 行时不抛错，审计却写了「已终止」——审计与事实背离 | 仓储返回 boolean；服务层失败抛 `CONCURRENT_MODIFICATION`（409），不写审计 |
| H-2 | **org EXCLUDE 竞态接不住**：PostgreSQL 排他约束违约是 SQLState 23P01，Spring 只把 23505 映射为 `DuplicateKeyException`——并发重叠任职的失败方 500 | 增捕 `DataIntegrityViolationException`，按约束名分流 `DUPLICATE_EMPLOYEE_NUMBER` / `ENGAGEMENT_PERIOD_OVERLAP` |
| H-3 | **knowledge 版本号竞态 → 500**：`SELECT max+1` 的读-然后-写在并发提案下撞唯一约束，无任何 catch | 冲突时重读重试一次；辅助方法带注释说明约束是仲裁者 |
| C-1 | **knowledge 负载不可读**：`RevisionResponse` 缺 `payloadMarkdown`——pin 了 Revision 却拿不到语义负载，K1 读语义空转 | DTO 增加字段 |

### 契约一致性（High/Medium）

| # | 发现 | 修复 |
|---|---|---|
| H1c | AI 网关 scope 不足返回 400 `INVALID_CONTEXT`（其余 8 模块全部 403） | 改 `FORBIDDEN` |
| H2c | ai-agent 切片无模块错误码，内联中文回显用户输入 | 新增 `AiAgentErrorCode`（`AINER.AI_AGENT.*`），去输入回显 |
| H4c | ActingGrant 复用 `SET_BINDING_NOT_FOUND`（错误码与消息张冠李戴） | 新增 `ACTING_GRANT_NOT_FOUND` / `ACTING_GRANT_ALREADY_REVOKED` |
| H5c/M4 | `SCOPE_` 前缀三层不一致（workspace 带前缀、其余不带、一处剥前缀兼容、一处死常量） | 常量统一裸 scope 值；删剥前缀逻辑与死常量；前缀只存在于 Spring Security 适配层 |
| H3c/M2/M3 | 分页三信封三行为：org/knowledge 静默收敛 + 回显未收敛的 size、`INVALID_PAGE` 成死码 | 收敛改为 422 拒绝；回显钳制后的值；workspace 的 INVALID_PAGE=400 保持（已发布行为） |
| M1c | org `createPosition` 撞码复用 `DUPLICATE_UNIT_CODE`（「单元编码已存在」实际是岗位撞码） | 新增 `DUPLICATE_POSITION_CODE` |
| M5c | knowledge sources/evidence 零校验（ADR-0044 承诺 namespaced source type；非法值 500） | linkType 枚举 + sourceType namespaced 正则 + 长度校验 |
| M-2 | org/agent 读方法缺 `@Transactional(readOnly = true)`（基准模块都有） | 13 个方法补齐 |
| M-8 | org `createUnit` 空死分支 + `ROOT_UNIT_CONSTRAINT` 死错误码 | 清除 |

## 遗留 follow-up（记录，按需排期）

下表保留 2026-08-19 评审时的发现原文。**后续关闭**见下一小节，不要把本表仍当成待办。

| # | 发现 | 严重度 | 说明 |
|---|---|---|---|
| M3 | workspace ALLOWED 审计先于业务写提交（REQUIRES_NEW），失败操作留下「已允许」审计 | Medium | 涉及 7 个方法重构，风险收益比需单独评估；DENY 路径语义正确 |
| M2 | SubjectSet 自提权防护是创建瞬间的快照；管理者之后被安排进岗位可回溯获得授权；成员解析 UNAVAILABLE 时该防线放行 | Medium | 需要设计决策（成员关系变化告警/复核钩子）；check() 的实时复查部分兜底 |
| M1 | ActingGrant 签发子集校验的合成锚点（`workspace.anchor`）可被伪造的 RESOURCE 绑定满足 | Medium | `check()` 实时复查兜底（其按真实资源复查）；需要保留 resourceType 拒绝列表 |
| M5/M5' | 授权引擎在 ainer-server 生产装配中因无 policy/ceiling 注册而全 deny；`@AinerAuthorize` 在控制器零使用；ActingGrant.check 无生产调用方 | Medium | **M5 已于 2026-08-20 关闭**：参考装配注册 18 项平台权限 + 恒等天花板 + BINDING_REQUIRED 策略 + 管理面白名单 + 目录同步，`AinerServerAuthorizationLivePathTest` 证明 Binding→ALLOW→撤销→DENY 全链活。M5'（@AinerAuthorize 端点消费与 check() 网关接线）仍留给产品 |
| L 系 | workspace 域枚举直出 API、@Nullable 缺失、内联 FQN、notification 匿名 Map 响应、ai-agent 分页无信封、注释语言中英文按模块分裂（基准英文 vs 新模块中文） | Low | 建议一次「客户可读性拉齐」批处理；注释语言方向（全中文或全英文）需负责人拍板 |
| H5' | Access token 在线校验与 step-up 默认关闭（`AINER_SECURITY_*_ENABLED:false`） | Low | 撤销即时生效依赖 DB 直查（无缓存）已验证；在线校验建议在部署文档标注为上线必选项 |

### 后续关闭（2026-08-20 / 2026-08-25）

| # | 状态 | 关闭说明 |
|---|---|---|
| M1 | 已关闭（2026-08-20） | `ScopeRequests.buildScope` 拒绝保留 resourceType（`workspace.anchor` / `request`），`ScopeRequestsTest` 覆盖 |
| M2 | Alert 切片关闭（2026-08-25） | UNAVAILABLE 创建拒绝保持；入岗命中自建 `workforce.position#assignee` 绑定写 `DELAYED_SELF_ELEVATION` 审计与计数器（ADR-0050）。不自动撤销、不阻断入岗 |
| M3 | 已关闭（2026-08-20） | 7 个写方法的 `auditAllowed` 移到业务写成功之后 |
| M5 | 已关闭（2026-08-20） | 见上表原文 |
| M5' | 参考接线关闭（2026-08-25） | Workspace/AI 端点消费 `@AinerAuthorize`；网关代行调用 `ActingGrant.check`。不是 1.x 资源级授权合同 |
| L 系 | 已关闭（2026-08-19/20） | 注释统一中文；DTO/`@Nullable`/分页信封已拉齐 |
| H5' | 已关闭（文档，2026-08-25） | 默认值仍为 `false`。`operations.md` / `configuration.md` / `security.md` 已把在线校验与 step-up 标为生产签发前必选项 |

**仍开放（需产品或设计决策，本轮不改代码）**

- **M2 Recheck / 禁止 bind-before-assignment**：ADR-0050 未选项，等真实 HR 流程消费者
- **M5' 后续**：把 permission 改成类型化 resourceType、方法级 AOP、obligation executor；
  Workspace 路径解析器与已装配业务 Controller 粗门禁已在参考装配落地，仍不是 1.x
  资源级授权合同

## 评审确认无问题的关键控制点

- SQL 全参数绑定（零拼接）；持久化路径零 `UUID.randomUUID()`；零 TODO/FIXME/调试残留
- 决策引擎核心：PUBLIC+systemOnly 拒绝先于公共策略、GLOBAL 仅 SERVICE、绑定所有权防御、CHALLENGE 永不算 ALLOW
- 防提权矩阵：requireManager 五连检、直连自授拒绝、角色自改拒绝（含未来期）
- Workspace OWNER 不变量：FOR UPDATE + 同事务先降后升 + SQL 角色守卫 + 部分唯一索引——未发现破坏路径
- 组织模块数据库设计：复合 FK 防跨目录引用、btree_gist EXCLUDE、调岗单事务——三模块中最扎实
- 全仓无 `IPage`/`com.baomidou` 类型泄漏进 API 层；无 entity/Row 泄漏进 Controller 签名

## 模块质量判定（评审时点）

| 模块 | 判定 |
|---|---|
| organization | 数据库设计扎实、注释解释「为什么」水准高；修复 H-1/H-2 后达标 |
| knowledge | 服务层纪律最好；修复 C-1/H-3 后达标 |
| ai-agent 切片 | 安全语义无缺陷（fail-closed、retire 即拒）；工程一致性最弱，本轮补错误码/readOnly 后仍建议按 file/dictionary 模式重梳 DTO/分页 |
| workspace / authorization / P3 四件套 | 既有基准水平，本轮修复审计/提权缺口 |
