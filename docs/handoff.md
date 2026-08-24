# Ainer Boot 项目交接文档

> 文档类型：交接快照 · 状态：生效 · 核对时间：2026-08-24 · 工程版本：`1.1.0`（发布中，见 §10）
>
> 本文面向接手项目的开发者或 AI 代理。读完本文应能：理解项目定位与当前状态、完成首次
> 构建、知道去哪找细节、避免踩过的坑。

## 1. 项目是什么

**Ainer Boot**（AI-Native Extensible Runtime Boot）是一个 **AI 原生但不局限于 AI** 的
通用企业 Java 脚手架：JDK 25 + Spring Boot 4.1 + PostgreSQL 18 的模块化单体，自带可信的
身份、授权、工作区治理、AI 模型网关与企业基座。

**商业定位**：付费客户将直接阅读源码——代码质量标准是「优秀的商业级脚手架」，不是「能跑
就行」。仓库当前为私有/专有，公开发行/开源决策未做。

**核心价值**：产品团队从「第一个业务提交」开始，而不是从「搭后台」开始。已通过 Project
Initializer 实现声明式生成新项目（manifest v1，确定性输出）。

## 2. 当前状态（诚实盘点）

### 已完成

| 能力域 | 状态 | 关键 ADR |
|---|---|---|
| 现代运行基座（JDK25/Boot4.1/PG18/虚拟线程/Maven4） | Stable | ADR-0026/0027/0029 |
| OAuth 2.1/OIDC + Passkey + typed token | Stable | ADR-0033 |
| Workspace membership 治理 + OWNER 转移 + 审计 | Stable | — |
| ADR-0037 混合授权 + SubjectSet + ActingGrant | Stable + Incubating 扩展 | ADR-0037/0042/0043 |
| AI 模型网关（SSE/预算/费用审计）+ Agent 注册表 | Stable + Incubating | — |
| P3 企业基座（文件/字典/配置/通知/缓存） | Stable | ADR-0039/0040 |
| 组织目录（Unit/任职/岗位/撤岗即失权） | Incubating（O1/O2 已交付） | ADR-0042 |
| Knowledge Foundation（不可变 Revision/人工发布） | Incubating（K1/K2 已交付） | ADR-0044 |
| Project Initializer + CLI | Stable | ADR-0035/0036 |
| OpenAPI 运行时文档（springdoc 3.1.0） | Stable（仅 ainer-server 装配） | — |
| 供应链（GPG 签名/SBOM/provenance/immutable Release） | Stable | ADR-0041 |
| 版本策略 + LTS 条款 | Stable | ADR-0045/0046 |

### 发布历史

| 版本 | 日期 | 要点 |
|---|---|---|
| `v1.1.0` | 2026-08-24（发布中） | 商业级评审修复 + P4 任务调度引擎（ADR-0047）+ 授权管理面收口；原 08-21 tag 因 Packages 配额未部署，重打 tag 后重新发布 |
| `v1.0.0` | 2026-08-18 | 1.0 产品合同定稿（零代码差异合同冻结） |
| `v0.2.0` | 2026-08-18 | G3 四切片（组织/Agent/Knowledge） |
| `v0.1.0` | 2026-08-14 | 第一个稳定 0.1 基线 |
| `rc.2` / `rc.3` | 2026-08-13 | 合格受控 RC（升级链历史起点） |
| `rc.1` | 2026-08-13 | **withdrawn/non-qualifying**，禁止消费 |

### 参考消费者（工程验证仓库，非生产系统）

| 仓库 | 位置 | 验证内容 |
|---|---|---|
| `xq-platform-next` | `~/01-code/xq/xq-platform-next` | Initializer 生成 + 完整升级链 `rc.2→1.1.0` 含回滚 + JWT/授权/SDK 纵向切片 |
| `python-learning-service` | `/Users/xq/01-code/self/python-learning-service` | Initializer 生成 + 冷仓接入 `0.1.0→1.1.0` + Evidence 存档切片 |

**重要**：两个消费者目前都是工程验证仓库，**没有部署运行，没有真实终端用户**。升级矩阵
和兼容验证是工程证据（证明脚手架「可以被消费」），不是「正在被消费」。

### 已知边界（记录在案，非缺陷）

- M2 延迟自提权（先签集合绑定、再安排岗位——`check()` 实时复查部分兜底）
- M5'（`@AinerAuthorize` 端点消费与 `ActingGrant.check` 网关接线属产品装配责任）
- GitHub Packages 私有仓库存储配额（免费版 500MB 含 Actions 制品，多次发布后可能耗尽）
- 分支保护未启用（private + GitHub 免费版限制；升级 Pro 或转 public 可解锁）

## 3. 快速上手

```bash
# 1. 克隆并验证
git clone git@github.com:ainerlab/ainer-boot.git && cd ainer-boot
export DOCKER_HOST=unix:///Users/$(whoami)/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
./mvnw clean verify

# 2. 本地运行
docker compose up -d && bash scripts/generate-dev-keys.sh
./mvnw -pl ainer-server spring-boot:run
# 另开终端 → Authorization Server
./mvnw -pl ainer-authorization-server spring-boot:run
```

完整步骤见 [`development.md`](development.md)。

## 4. 架构要点（30 秒版）

```
ainer-core ← ainer-spring ← starter-* ← module-* ← server
                  ↑                              ↑
            ainer-security              ainer-authorization-server
```

- **依赖方向**：framework 不依赖业务模块；业务模块通过显式契约交互
- **模块化单体**：默认交付形态；按需服务化（`monolith|service` 只切适配器）
- **PostgreSQL 18 唯一数据库**：Flyway migration 只向前追加；UUIDv7 持久化身份
- **安全模型**：独立 OAuth 2.1/OIDC AS → JWT Resource Server → 模块 scope 强制
- **授权引擎**：ADR-0037 混合授权（RBAC+ReBAC+ABAC），拉取式无缓存——撤销即拒

详细架构见 [`architecture.md`](architecture.md)。

## 5. 必读 ADR（按优先级）

| 优先级 | ADR | 为什么重要 |
|---|---|---|
| ★★★ | [ADR-0040](decisions/0040-p3-enterprise-base-and-1.0-product-contract.md) | 1.0 产品合同：Stable/Incubating/非目标 + G0–G4 路线 |
| ★★★ | [ADR-0037](decisions/0037-post-greenfield-authorization-baseline.md) | 混合授权基线（产品核心卖点） |
| ★★★ | [ADR-0033](decisions/0033-account-workspace-subject-isolation-greenfield-baseline.md) | Greenfield 重置：去 tenant 化 + typed token |
| ★★☆ | [ADR-0041](decisions/0041-private-rc-supply-chain-and-immutable-release-baseline.md) | 供应链门禁（签名/SBOM/provenance/immutable） |
| ★★☆ | [ADR-0045](decisions/0045-versioning-lts-and-patch-baseline.md) | 版本策略 + 升级回滚窗口 + patch 规则 |
| ★☆☆ | [ADR-0042](decisions/0042-organization-directory-greenfield-baseline.md) | 组织目录（Incubating） |
| ★☆☆ | [ADR-0043](decisions/0043-agent-delegation-greenfield-baseline.md) | Agent 代行（Incubating） |
| ★☆☆ | [ADR-0044](decisions/0044-knowledge-foundation-implementation-baseline.md) | Knowledge（Incubating） |

完整索引见 [`decisions/README.md`](decisions/README.md)（47 个 ADR）。

## 6. 常见坑（踩过的）

| 坑 | 症状 | 解法 |
|---|---|---|
| Colima 环境变量缺失 | 105 tests skipped | 设 `DOCKER_HOST` + `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` |
| `surefire:test` 不重编译 | 改了代码测试结果不变 | 用 `test` 或 `verify` phase，不要单用 `surefire:test` |
| 跨测试 fixture 泄漏 | 离奇 403/404 或 bean 冲突 | 嵌套 `@TestConfiguration` 加 `@ConditionalOnProperty` 守卫 |
| `SCOPE_` 前缀不一致 | JWT 有 scope 但 403 | JWT scope claim 用裸值（无前缀）；常量也不带前缀 |
| timestamptz 精度漂移 | CI 422（本地通过） | 服务层时间入口统一 `truncatedTo(ChronoUnit.MICROS)` |
| 模块 `@MapperScan` 退避 | 自定义 mapper 不注册 | 授权模块的显式 `@MapperScan` 会让 MyBatis 自动扫描退避；宿主需自己声明 |
| GitHub Packages 配额 | deploy HTTP 402 / 包内容 404 锁定 | 免费版 500MB 共享存储，包体已 ~377MB；发布前清空缓存（housekeeping 工作流每周兜底），发布构建已去缓存；根治需付费额度或删历史版本（见 project-status 2026-08-24 记录） |
| jsonPath 数字断言 | `expected: 2L but was: 2` | jsonPath 返回 Integer 不是 Long |

## 7. 测试与质量

- **全量**：`./mvnw clean verify` → 415 tests / 0 failure / 0 error / **0 skipped**
- **真 JWT 链**：全部业务模块的 HTTP 测试用 `JwtTestSupport`（RSA 3072 验签，无 stub）
- **CI 三门禁**：Maven 4 质量门禁 + gitleaks + 虚拟线程矩阵（非阻塞）
- **商业级评审**：已完成三路语义评审（安全/正确性/契约一致性），审计快照见
  [`reviews/2026-08-19-commercial-grade-code-review.md`](reviews/2026-08-19-commercial-grade-code-review.md)

## 8. 开发规范速查

- **注释**：中文（技术名词/类名/SQL/ADR 保留英文）——已全仓统一
- **错误码**：`AINER.<MODULE>.<ERROR>` 稳定字符串 + 真实 HTTP 状态码
- **scope**：`module.read` / `module.manage`（裸值，无 `SCOPE_` 前缀）
- **分页**：`page ≥ 1, 1 ≤ size ≤ 100`，越界 422
- **乐观锁**：CAS boolean + 409 CONCURRENT_MODIFICATION
- **审计**：业务写成功后（不在之前）
- **提交**：`type(scope): 中文描述`

完整规范见 [`conventions.md`](conventions.md)。

## 9. 发布操作

1. 发布准备 PR（CHANGELOG + README + project-status 版本行）→ 合入 → dev CI 绿
2. **确认存储配额**：`gh api /repos/ainerlab/ainer-boot/actions/caches --jq '.total_count'`
   必须为 0；发布窗口内不要有并发 CI（它们会在结束时写缓存）
3. `git tag -a v<version> <merge-commit> -m "..."` → `git push origin v<version>`
4. Release workflow 自动执行完整门禁（签名 deploy → 远端读回验签 → 空仓消费者 →
   SBOM/provenance → immutable Release）；发布构建已去 Maven 缓存，不会自我毒化
5. 双消费者升级矩阵验证

**发布前检查**：Packages 配额充足（包体基线 ~377MB + 本版 ~65MB < 500MB）；目标版本远端不存在（404）。

## 10. 当前待办

| 优先级 | 事项 | 状态 |
|---|---|---|
| 高 | **退役 rc 链制品 + 解除配额锁定**（ADR-0048 已接受，待执行删除） | 需 `write:packages` 授权后执行 26 包 × 3 版本删除 |
| 高 | **1.1.0 发布收尾** | tag 已重打到 `8587519` 且 workflow 修复就绪；锁定解除后 `gh run rerun 32711952544` 即可 |
| 中 | 双消费者 `1.0.0 → 1.1.0` 升级矩阵 | 等 1.1.0 发布后执行（两仓库均在本机） |
| 中 | 分支保护治理（GitHub Pro 或转 public） | 需负责人决策 |
| 低 | Incubating → Stable 晋升评估 | 等第二个消费者兼容验证积累 |
| 低 | AI Runtime A2-A4 / Knowledge Phase 2-4 | 按真实产品需求拉动 |

已完成：P4 任务调度模块（ADR-0047）并入 1.1.0；授权端点门禁类型化目标解析 +
RFC 9470 挑战头 + ArchUnit 守护（ADR-0037 后续切片首批，PR #35）；CI 存储纪律
（housekeeping 工作流 + 发布构建去缓存，PR #36）。

## 11. 关键文档地图

```
入口
├── README.md                          产品入口（当前版本 + 模块表）
├── docs/00-overview.md                文档权威入口 + 阅读路线
├── docs/ainer-boot-1.0-product.md     1.0 产品说明（合同快照）
├── docs/project-status.md             当前状态 + 验证记录 + 下一里程碑
└── docs/handoff.md                    本文（交接文档）

开发
├── docs/development.md                日常开发操作手册
├── docs/conventions.md                编码规范（含注释语言标准）
├── docs/testing.md                    测试策略 + Colima 配方
├── docs/architecture.md               模块架构 + 数据所有权
└── docs/api.md                        HTTP API 契约

数据与安全
├── docs/database.md                   数据库手册
├── docs/database-design-standard.md   数据库设计规范
├── docs/security.md                   安全专题
└── docs/dependencies.md               依赖台账（许可证/版本/用途）

运维与发布
├── docs/releasing.md                  发布流程
├── docs/operations.md                 运维手册
├── docs/development-environment-deployment.md  开发环境部署
└── docs/configuration.md              配置参考

决策
├── docs/decisions/README.md           ADR 索引（47 个）
└── docs/reviews/                      审计快照
```

## 12. AI 代理协作须知

如果你是 AI 代理（Claude/Codex/Cursor 等）接手本项目：

1. **先读 `AGENTS.md`**——包含最高优先级规则（Clean-room、依赖方向、安全红线等）
2. **用中文沟通**——AGENTS.md 明确要求
3. **`./mvnw clean verify` 是合并前唯一门禁**——任何改动都必须全量验证
4. **不要用系统 Maven**——生产者构建只用仓库 Wrapper
5. **不要引入 Mockito/H2/Sa-Token**——仓内零依赖且明确禁止
6. **不要用 `UUID.randomUUID()`**——持久化身份统一 `Uuidv7.generate()`
7. **测试 fixture 加守卫**——嵌套 `@TestConfiguration` 会跨测试泄漏
8. **提交格式**：`type(scope): 中文描述`
9. **遇到不确定的设计决策**——先查 ADR，没有覆盖就提新 ADR，不要改已接受的 ADR 结论
