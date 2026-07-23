# ADR-0001：自主架构基线与竞品隔离

- 状态：Accepted
- 日期：2026-07-22

## 背景

Aurora 初版文档把 BladeX、RuoYi-Vue-Pro、Dante 和 Snowy 的若干机制组合成目标架构，并出现了以下问题：

- 把 Dante 描述为同一个应用通过一行 YAML 完成单体/微服务切换；
- 先声称 Dante 没有 `MonolithBusBridge`，但本地代码实际存在该类；
- 把 Snowy 的 SQL 拼接、源码密钥混淆和固定 IV 当成可吸收的工业方案；
- 同时要求关闭循环依赖，又在 Boot 4 示例中开启它；
- 声称 DTO 可由 MapStruct 生成；
- 把 password grant 列为 Spring Authorization Server 的标准授权类型；
- 计划一次性重写 xiaoqu 约 57 万行代码。

这些表述会把竞品实现细节误当成 Aurora 的事实基础，并产生不现实的交付承诺。

## 已核实事实

### Dante

Dante 有独立单体应用 `dante-monolith-application`，微服务有独立 UAA、UPMS 等可执行应用；共享 engine 和自动配置。`@ConditionalOnArchitecture` 用于部分适配器和消息配置，不负责自动完成服务拆分。

本地 `dante-cloud-modules/dante-monolith-autoconfigure/.../MonolithBusBridge.java` 确实存在一个空 `BusBridge` 实现，用于避免单体连接 Kafka。

因此正确抽象是“共享业务组件 + 独立发行物 + 条件适配器”，不是“同一应用 YAML 切换全部架构”。

### Snowy

Snowy 的 plugin 是 Maven 物理模块，`snowy-web-app` 静态依赖实现模块，不是动态插件市场。其 API 大量返回 `JSONObject`；数据权限包含字符串 SQL；字段加密密钥和 IV 可从源码恢复。这些不能成为 Aurora 的工业安全基线。

### BladeX

BladeX 的代码和模板受商业许可限制，明确禁止构建和销售同类衍生框架。Aurora 只能研究其产品分层和交付方式，不能复制实现。

### RuoYi-Vue-Pro

RuoYi-Vue-Pro 的 MIT 代码可作为功能和 Boot 4 兼容参考，但其循环依赖、统一 200、弱模块边界和 H2 MySQL-mode 测试不适合作为 Aurora 架构基础。

## 决策

1. Aurora 使用 clean-room 自主实现。
2. 模块化单体是第一发行物。
3. 服务化通过独立可执行装配和明确数据所有权演进。
4. `aurora.runtime.mode` 仅选择基础设施适配器。
5. `aurora-core` 保持零 Spring 依赖。
6. HTTP status 表达真实协议语义，稳定业务码使用字符串命名空间。
7. OAuth2/OIDC 采用标准支持的授权类型，非标准登录通过明确扩展点实现。
8. 数据权限不能绑定 URL 字符串或拼接 SQL。
9. 字段加密未来采用 KMS/信封加密、认证加密、唯一 nonce 和 key version。
10. xiaoqu 使用渐进切片迁移，不做大爆炸式重写。

## 后果

正面：

- Aurora 不受竞品架构和许可证约束。
- 设计承诺可以由代码和测试逐项验证。
- 服务化、认证和 AI 能力拥有清晰的演进边界。
- 商业发行可以建立干净的知识产权来源记录。

代价：

- 不能通过搬运竞品代码快速堆功能。
- 需要为协议、Starter、迁移和兼容性投入更多自动化测试。
- 旧 xiaoqu 与 Aurora 会在一段时期并行运行。

## 合规记录

每次参考外部项目时，仅记录：问题、公开行为、标准来源、取舍与独立验收条件。禁止在 Aurora 文档中保存可直接复刻的受限源码片段。
