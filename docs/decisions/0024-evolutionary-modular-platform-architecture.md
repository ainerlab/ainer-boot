# ADR-0024：演进式模块化平台架构

- 状态：Accepted
- 日期：2026-07-30
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

Ainer 已经形成 BOM/Starter、Identity、Workspace、AI Runtime、独立 Authorization Server 与
Resource Server，但尚未用一个统一、准确的架构名称说明这些部分如何协作。DDD、六边形架构、
模块化单体和微服务也经常被当成互斥选项，容易导致以下问题：

- 为了宣称采用 DDD，给简单 CRUD 机械增加接口、聚合和映射层；
- 为了宣称采用六边形架构，为每个类制造没有替换价值的 port；
- 为了宣称支持微服务，提前创建网关、注册中心、Feign、MQ 和大量空模块；
- 把部署拓扑、领域建模和依赖方向混为同一件事；
- 让具体产品语义进入通用平台模块。

曾考虑过更强调工程验证过程的名称，但它不能准确表达 Ainer 的产品和工程定位，也容易让人误以为
核心是审计或调查。真正需要表达的是：Ainer 从清晰模块开始，并在满足明确条件时按需演进部署
边界。

本 ADR 补充 [ADR-0001](0001-independent-architecture-baseline.md)，不改变其 clean-room、
模块化单体优先和独立发行物结论。

## 决策驱动因素

- 给维护者、脚手架使用者和生成器提供一致的架构语言；
- 让架构规则可以通过 Maven、ArchUnit、测试和独立消费者验证；
- 保持领域、事务、数据所有权与外部依赖边界；
- 避免过度分层、接口爆炸和微服务优先；
- 允许系统在真实扩容、隔离、组织和发布需求出现后继续演进；
- 保证 Ainer 平台能力与 `xq-platform-next` 等产品能力分离。

## 备选方案

### 完整战术 DDD 模板

要求每个功能都具备 Entity、Value Object、Aggregate、Repository、Domain Service 和事件。
它适合规则复杂的领域，但会让普通配置、查询和 CRUD 产生大量仪式性代码，因此不采用。

### 全面六边形架构

要求每个应用服务、Repository 和外部调用都先建立接口。它能强化依赖反转，但在没有第二实现、
外部边界或测试替身需求时会制造接口爆炸，因此不采用。

### 微服务优先

从网关、注册中心、远程调用和消息中间件开始拆分所有业务模块。该方案提前引入网络失败、
分布式一致性和运维成本，且当前没有相应组织与容量需求，因此不采用。

### 普通模块化单体

只约定一个进程和若干 Maven 模块，但不规定领域、数据所有权、外部依赖和未来拆分条件。
该方案不足以支撑 Ainer 作为可复用平台与脚手架，因此不单独采用。

## 决策

Ainer 的正式架构名称为：

> **演进式模块化平台架构（Evolutionary Modular Platform Architecture）**

它的完整定义是：

> Ainer 是制品化的平台内核，以模块化单体为默认运行形态，以领域边界组织业务模块，以端口和
> 适配器隔离外部依赖，以独立 Authorization Server 建立身份安全边界，并在满足明确拆分条件后
> 按需演进为独立服务。

### DDD 的使用范围

- 使用战略 DDD 识别限界上下文、上下游关系、统一语言和数据所有权；
- 只在存在状态机、不变量、并发规则或复杂决策时使用聚合、值对象、领域服务和领域事件；
- 简单 CRUD、配置和只读查询不机械套用完整战术 DDD；
- 数据库表、Controller 路径或 Maven 模块名称不能单独决定领域边界。

### 端口与适配器的使用范围

- HTTP、定时任务和消息消费者是入站适配器；
- PostgreSQL/MyBatis、Identity Directory、AI Provider、微信、对象存储和旧系统是出站适配器；
- application 负责用例编排和事务边界，domain 负责业务规则与不变量；
- domain 不依赖 Spring、Web、MyBatis 或厂商 SDK；
- port 只在外部边界、跨模块契约、存在多个实现或需要稳定测试替身时创建；
- 不要求每个应用服务都先创建同名接口。

### 默认运行与部署形态

- 默认业务运行形态是模块化单体；
- Authorization Server 因身份、协议数据、密钥和生命周期边界保持独立发行；
- 每个业务模块拥有自己的表、migration、Repository、事务和公开契约；
- 模块之间不得直接查询私有表、访问 Mapper 或注入实现类；
- `ainer.runtime.mode` 只能选择当前发行物中的 local/remote adapter，不能自动拆库或生成微服务。

### 服务拆分条件

服务拆分必须同时满足一项业务或运行触发条件，以及全部工程准备条件。

触发条件至少一项：

- 需要独立扩缩容；
- 需要安全、合规或故障隔离；
- 需要独立团队、发布节奏和运行责任；
- 单体资源竞争无法通过进程内治理解决。

工程准备条件必须全部满足：

- 数据所有权明确，不依赖跨模块私表查询；
- 已有稳定 port、API 或事件合同及契约测试；
- 已定义超时、重试、幂等、失败和版本兼容语义；
- 已具备 migration、可观测性、发布、回滚和运行责任。

拆分通过独立可执行装配和 remote adapter 完成，不改变领域规则，也不承诺通过一个配置完成单体
到微服务的转换。

### 平台与产品边界

- Ainer 只拥有稳定、跨产品复用的 framework、starter、平台模块和生成原语；
- 产品消费者拥有自己的业务语义、状态机、API、数据和外部适配器；
- 产品能力只有在多个独立消费者中语义稳定、边界一致且不泄漏产品规则时，才评估上提为 Ainer
  公共契约；
- `xq-platform-next` 是首个外部消费者，不是 Ainer 源码副本或长期 fork。

## 后果

### 正面

- DDD、六边形架构、模块化单体和微服务各自拥有明确职责，不再互相替代；
- 生成项目可以保持较少层次，同时为复杂领域保留扩展空间；
- 产品模块和平台模块的晋升条件更清晰；
- 当前可以保持低运维成本，未来仍能按需拆分；
- 架构约束可以通过自动化检查，而不是依赖命名或口号。

### 负面与风险

- 维护者必须持续判断业务复杂度，无法只靠固定模板做所有决定；
- local adapter 演进为 remote adapter 时仍需处理真实分布式系统问题；
- 模块化单体如果缺少依赖和数据所有权检查，仍可能退化为紧耦合单体；
- 为保持独立消费者兼容，需要投入 BOM、契约、版本和生成器测试。

## 安全、数据与隐私

- 本决策不改变现有 OAuth/OIDC、tenant、actor 和资源授权规则；
- Identity、Workspace、AI Runtime 与产品模块继续拥有各自数据；
- 跨运行时身份与撤销使用服务身份、最小 scope、可靠事件和幂等消费；
- 服务拆分不能成为绕过 tenant 隔离、数据保留、审计或秘密管理的理由；
- 产品主体与平台 tenant/member 只有在语义一致时才允许复用。

## 运维与迁移

- 本决策不立即增加运行进程、中间件或数据库；
- 现有 `ainer-server` 与 `ainer-authorization-server` 拓扑保持不变；
- 文档、项目初始化器、模块模板和 ArchUnit 规则逐步采用本 ADR 的术语与约束；
- 现有模块若不满足依赖或数据所有权规则，通过正常重构修正，不进行大爆炸式重写；
- 新服务拆分必须先通过本 ADR 的触发条件与工程准备条件检查。

## 验收方式

- Maven 与 ArchUnit 阻止 domain 依赖 Web/MyBatis、Starter 依赖业务模块和模块间实现依赖；
- PostgreSQL 集成测试验证模块 migration、事务和数据所有权；
- ApplicationContextRunner 验证可选 Starter 与 local/remote adapter 装配；
- API/事件契约测试验证跨边界兼容；
- project initializer golden consumer 验证外部项目只通过已发布制品消费 Ainer；
- 任何服务拆分设计必须逐项记录触发条件、工程准备状态、回滚方式和运行责任。

## 参考

- [Ainer 架构总览](../architecture.md)
- [Ainer 平台架构设计](../design/ainer-scaffold-design.md)
- [Ainer 工程范式](../design/paradigm-redesign.md)
- [ADR-0001：自主架构基线与竞品隔离](0001-independent-architecture-baseline.md)
- [xiaoqu-platform → Ainer 渐进迁移路线](../migration/ainer-migration-plan.md)
