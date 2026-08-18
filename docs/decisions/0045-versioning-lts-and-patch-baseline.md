# ADR-0045：版本策略、LTS 与补丁支持基线

- 状态：Accepted
- 日期：2026-08-18
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

ADR-0040 定义了 1.0 Product Contract 与 1.x 兼容承诺（HTTP API/错误码/SPI/migration 只向前
追加），但未定义版本节奏、补丁线与升级支持窗口。G4（1.0 候选）门禁要求「连续升级与回滚、
HTTP/Java/schema/config 兼容检查、LTS/补丁策略」成文。2026-08-18 `v0.2.0` 发布与双消费者
升级矩阵（见验收）为本文提供了首批真实证据。

## 决策

### 1. 版本语义（pre-1.0 与 1.x）

1. **0.x 阶段**：minor（0.1.0 → 0.2.0）允许加性变更与少量非稳定面的破坏（Incubating 层，
   ADR-0040 已声明不承诺兼容）；Stable 层即便在 0.x 也保持只加不破——这是脚手架在自有
   消费者还少的阶段能给出的最强承诺，且已由 0.1.0 → 0.2.0 验证。
2. **1.x 阶段**：ADR-0040 兼容承诺全量生效；破坏性变更必须新 major 并保留迁移路径。
3. 版本号只描述制品契约，不承载部署拓扑或模块拆分语义。

### 2. 升级与回滚支持窗口

1. 官方支持**相邻 minor 升级**（N → N+1）与**一级回滚**（N+1 → N 作为有效回滚终点）；
   跨多级升级需逐级验证，不承诺直接跳级。
2. 每次发布的验证证据必须包含：发布门禁全绿（签名 deploy、全量制品读回验签、空仓消费者、
   Initializer 三通道、SBOM/provenance、immutable Release）+ 至少一个真实消费者在该版本
   上的完整测试通过。
3. **连续升级/回滚矩阵**（G4 门禁）：两个独立参考消费者各自完成一次 N → N+1 升级验证，
   其中至少一个完成 N+1 → N 回滚验证；证据记录在 `docs/project-status.md`。

### 3. 补丁线与 LTS

1. **patch（x.y.Z）**：只修缺陷与安全问题，零 API/配置/schema 变化（migration 也不得新增）；
   从对应 minor 线的 dev 分叉或 cherry-pick 产生，走同一发布门禁。
2. **0.x 阶段不设正式 LTS**：每个 minor 的最后一个 patch 即该线终点；消费者应随 minor
   升级，停留旧线的支持窗口为一个 minor 周期。
3. **1.0 起的 LTS**（届时另立 ADR 细化）：每个 major 的首个 minor 为 LTS 线，安全补丁
   窗口与扩展支持条款在 1.0 发布前定稿；在此之前本文的 patch 规则先行适用。
4. 安全补丁不改变兼容面；如必须破坏兼容修复安全缺陷，按新 major 处理并发布公告。

### 4. 兼容检查的落地形态

1. **HTTP/Java 兼容**：双参考消费者的完整测试套件在新版本上的全绿即真实证据（它们覆盖
   统一响应信封、真实状态码、错误码字符串、JWT 安全链与 scope 语义）。
2. **schema 兼容**：migration 只向前追加由发布评审保证；消费者侧从空库重放全部 migration
   （产品 V1/V2 + 模块基线）在其测试中强制执行。
3. **config 兼容**：公开配置项由 `spring-configuration-metadata.json` 契约化并纳入消费者
   门禁（P0-3）；移除/改名公开配置键视同破坏，需升 minor 并在 CHANGELOG 显著标注。
4. 上述证据与发布记录同轮归档，形成可审计链。

## 验收

- **2026-08-18 `v0.2.0` + 双消费者矩阵（首批证据）**：release run `32123318369` 全绿
  （122/122 制品读回验签、26 projects、SBOM/provenance、immutable Release 精确绑定
  `62829dc`）；`xq-platform-next` 完成 `0.1.0 → 0.2.0`（14/0/0/0，含 JWT 链/撤销传播/
  migration replay）与 `0.2.0 → 0.1.0` 回滚验证（14/0/0/0）后固定 `0.2.0`——累计升级链
  `rc.2 → rc.3 → 0.1.0 → 0.2.0`；`python-learning-service` 完成 `0.1.0 → 0.2.0`
  （8/0/0/0，隔离冷仓全部 0.2.0 制品远端解析）。两消费者业务域、测试面与接入路径完全
  独立，构成 §2.3 要求的矩阵。

## 参考

- [ADR-0040](0040-p3-enterprise-base-and-1.0-product-contract.md)（1.0 Product Contract 与兼容承诺）
- [ADR-0041](0041-private-rc-supply-chain-and-immutable-release-baseline.md)（发布门禁）
