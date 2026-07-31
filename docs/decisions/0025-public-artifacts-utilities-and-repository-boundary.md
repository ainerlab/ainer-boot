# ADR-0025：公共制品、工具类与仓库边界

- 状态：Accepted
- 日期：2026-07-30
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

BladeX-Tool、Dante Engine 等项目把大量公共代码、Starter、基础设施适配器乃至平台业务提取为
独立组件工程。这种结构能够复用代码和缩短主应用构建时间，但也可能产生万能工具包、传递依赖
膨胀、跨仓版本锁步、发布制品过多和产品语义泄漏。

Ainer 当前已经拥有 parentless BOM、`ainer-core`、`ainer-spring`、`ainer-security` 和
Web/Persistence/Security Starter。它缺少的是可对外发布、安全默认、兼容受控的制品产品线，
不是另一个包含 String、Collection、JSON、文件、HTTP、加密和所有云厂商 SDK 的大工具包。

`xq-shop-next` 与 `xq-zhiwu` 是同一个 `xq-platform-next` 后台的两个客户端。它们只构成 Ainer 的
一个外部后端消费者，不能作为立即拆分 `ainer-engine` 独立仓库的理由。

## 决策驱动因素

- 让 `xq-platform-next` 能通过 Maven 制品消费 Ainer，而不是复制源码；
- 保持公共 API、依赖、安全默认值和版本兼容可控；
- 避免 `common`、`tool`、`util` 成为无所有权的垃圾桶；
- 直接复用 JDK、Spring 和经过批准的第三方库，不重复维护通用算法；
- 让基础设施实现可以替换，但不把厂商 SDK 泄漏到领域和公共契约；
- 保持当前跨模块原子修改能力，避免过早引入跨仓发布成本。

## 备选方案

### 立即拆分独立 Ainer Engine/Tool 仓库

可以让 framework 看起来更加独立，但当前只有一个确定的外部后端消费者，公共 API、兼容政策和
发布流水线尚未稳定。拆仓会把普通重构变成跨仓版本协调，因此不采用。

### 建立万能 `ainer-tool`

将 String、Collection、JSON、文件、HTTP、Bean 映射、加密等静态工具集中在一个 JAR 中。它短期
使用方便，但会形成无边界 API、隐藏错误处理、全局状态和不相关传递依赖，因此不采用。

### 各模块随意选择和封装第三方库

不建立任何公共规则，让每个模块自行选择 JSON、HTTP、文件和集合工具。该方案会产生多个客户端、
配置漂移和许可证失控，因此不采用。

## 决策

### 公共组件产品形态

Ainer 建设小而清晰的 Framework/Starter/Test Support/Build Tools 制品线，暂时全部保留在
`ainer-boot` 同一个 Git 仓库：

```text
ainer-dependencies/

ainer-framework/
├── ainer-core/
├── ainer-spring/
├── ainer-security/
├── ainer-oauth2-client/                 # 从 Resource Server Starter 分离
├── ainer-starter-web/
├── ainer-starter-persistence/
├── ainer-starter-security/
└── ainer-starter-observability/         # 窄范围、后端中立

ainer-test-support/
└── ainer-test-support-postgresql/

ainer-build-tools/
├── ainer-project-initializer/
└── ainer-module-generator/               # 模式稳定后再实现

ainer-integrations/                       # 有真实用例后按 provider 建立
├── ainer-object-storage-api/
├── ainer-object-storage-s3/
└── ainer-wechat-miniapp/
```

目录表示制品分类，不代表应立即创建所有空模块。平台业务继续属于 Identity、Workspace、AI Runtime
等独立模块，xq 商品、员工、顾客、品控和交易语义不得进入 framework。

### 工具类原则

Ainer 采用：

> **封装政策，不封装语法糖。**

- 集合、字符串、日期、文件等通用操作优先使用 JDK；
- Spring 模块可以使用 Spring 官方工具，但不得让 Spring 类型进入 `ainer-core` 或 domain；
- JSON 使用 Boot 管理的 Jackson `ObjectMapper`，通过依赖注入配置，不建立全局静态单例；
- HTTP 使用 JDK `HttpClient`、Spring `RestClient` 或明确需要响应式时的 `WebClient`；
- 外部系统通过 `Gateway`、`Client`、`Repository`、`Codec` 等类型化端口访问；
- 公共端口表达身份、超时、错误、幂等、资源和安全策略，不只把三行调用缩短成一行；
- 不建立 `CollectionUtil`、`JsonUtil`、`FileUtil`、`HttpUtil`、`BeanUtil`、`DateUtil` 等万能静态入口。

具体替代选择和例外条件由
[第三方依赖与许可证台账](../dependencies.md#公共工具与标准能力选择) 维护。

### 第三方依赖规则

- 直接依赖必须进入 Ainer BOM 和依赖台账；
- 公共契约不得暴露厂商 SDK、第三方集合类型或具体 HTTP/JSON 客户端；
- 领域层不得依赖 Jackson、Spring HTTP、文件上传或云存储 SDK；
- 同一种基础能力在同一运行时只选择一个默认实现；
- 只有 JDK/Boot 能力无法满足明确需求时才新增第三方依赖；
- 新依赖必须完成许可证、漏洞、体积、维护状态和替代方案评估。

### 新制品准入条件

除 Web、Security、Persistence、Test 等平台基础能力外，新增公共制品必须满足：

1. 可以用单一能力名称说明职责，不能依赖 `common`、`tool`、`misc` 等模糊命名；
2. 不包含 `xq-platform-next` 或其他具体产品规则；
3. 公共 API 不泄漏实现 SDK；
4. 不强迫消费者引入无关运行时依赖；
5. 默认安全，功能关闭必须显式且不能静默放开受保护资源；
6. 有自动配置开/关/非法配置测试和至少一个独立 consumer smoke test；
7. 已纳入 BOM、依赖台账、版本兼容和发布流程；
8. 便利性抽象原则上需要至少两个独立消费者；基础平台契约可以由 Ainer 运行时和首个外部消费者
   共同验证。

### 对外发布前的收口

现有 framework 在作为公共制品发布前必须完成：

- Resource Server Starter 改为 fail-closed，移除属性缺失时的默认 `permitAll`；
- 移除 Workspace、AI 等产品路径默认值，由消费者显式提供；
- 将 Client Credentials Token 获取拆为窄范围 `ainer-oauth2-client`；
- 将 Identity/Workspace 业务 scope 移回所属模块；
- 补齐 Maven Wrapper、license、SCM、developers、source、Javadoc、签名、SBOM 和发布仓库；
- 建立 API/配置兼容检查与外部 golden consumer；
- Persistence 公共能力补齐 PostgreSQL 18、UUIDv7、`RETURNING` 和对应测试语义，不能只成为依赖聚合。

### 独立仓库拆分条件

只有同时满足以下条件，才评估把 framework 从 `ainer-boot` 拆为独立 Git 仓库：

1. 至少两个独立发布的后端产品持续消费公共制品；
2. 公共 API 和配置已连续两个版本保持稳定；
3. 已建立 SemVer、兼容矩阵、升级政策和弃用窗口；
4. Maven 发布、源码、Javadoc、签名、SBOM 和 consumer smoke 自动化稳定；
5. Framework 有独立 owner、安全补丁节奏和发布责任；
6. 独立发布带来的收益高于跨仓原子修改、调试和版本协调成本。

在此之前，Git 单仓与 Maven 多制品并不冲突。

## 后果

### 正面

- Ainer 可以形成自己的公共组件品牌，同时避免万能工具包；
- 消费者按能力选择依赖，不必携带所有集成与业务模块；
- JDK、Spring 和第三方库的安全修复可以直接跟随上游；
- 公共 API 聚焦 Ainer 的政策与契约，而不是重复通用语法；
- 当前仍可原子修改 BOM、Starter、模块和测试。

### 负面与风险

- 业务代码会直接使用一部分 JDK/Spring API，无法通过单个工具类统一所有写法；
- 类型化 Gateway/Codec 比静态工具方法需要更多设计；
- 公共制品发布需要额外维护兼容性、文档、源码和测试；
- 未来若真正拆仓，仍需完成坐标、发布和贡献流程迁移。

## 安全、数据与隐私

- 安全 Starter 的默认行为必须失败关闭；
- JSON、HTTP、文件和对象存储适配器必须执行大小、超时、内容类型、路径、重定向和秘密保护；
- 通用工具不得绕过 tenant、actor、审计、数据保留或输出脱敏；
- 加密不进入万能工具类，必须使用经过评审的算法、密钥来源、nonce、key version 和失败语义；
- 外部 SDK 只位于基础设施适配器，其对象不得进入领域模型或公开 API。

## 运维与迁移

- 本决策不立即拆仓，也不一次性创建全部候选模块；
- 先收紧现有 framework，再发布 SNAPSHOT 并由 `xq-platform-next` golden consumer 验证；
- 现有局部通用代码只有在满足新制品准入条件后才抽取；
- 新的对象存储和微信能力先从两个小程序的真实纵向切片建立，再决定公共契约；
- 不进行全仓“把所有工具调用替换成 Ainer 包装”的机械迁移。

## 验收方式

- `ainer-core` 的运行时依赖保持为空；
- Maven dependency tree 不出现未选择功能对应的 SDK；
- ArchUnit 阻止 domain 依赖 Jackson、Spring Web 和厂商 SDK；
- Starter 使用 ApplicationContextRunner 覆盖默认、显式开启、关闭和非法配置；
- 外部生成项目只通过 Maven 制品构建、测试和启动；
- 安全 Starter 缺少配置时不得形成匿名全放行；
- 依赖台账和 SBOM 能解释每一个直接运行时依赖。

## 参考

- [ADR-0024：演进式模块化平台架构](0024-evolutionary-modular-platform-architecture.md)
- [Ainer 架构总览](../architecture.md)
- [Ainer 工程约定](../conventions.md)
- [Ainer 第三方依赖与许可证台账](../dependencies.md)
- [Ainer 平台架构设计](../design/ainer-scaffold-design.md)
