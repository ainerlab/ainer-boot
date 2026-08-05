# Ainer 工程范式

> 版本：v1.0 · 2026-07-22
> 本文描述 Ainer 要避免的遗留风险和已选择的替代方案，不以攻击或兼容任何单一开源项目为目标。

## 1. 循环依赖

遗留风险：Service 互相注入，通过 `@Lazy` 和 `allow-circular-references=true` 维持启动。

Ainer：

- `allow-circular-references=false`；
- 跨模块只依赖公开契约；
- 反向通知使用领域事件或可靠消息；
- 同一聚合内互相循环的 Service 应合并或重新划分职责；
- `@Lazy` 不能作为依赖环修复手段。

## 2. 数据权限

遗留风险：只按表过滤，或者按 URL 字符串和用户输入拼接 SQL。前者粒度不足，后者存在脆弱耦合和注入风险。

Ainer：

- 授权输入为主体、动作、资源与上下文；
- 应用用例先执行资源级授权；
- 查询范围通过参数化策略、授权关系表或预计算关系实现；
- HTTP URL 不是领域权限主键；
- 缺失用户或策略默认拒绝。

## 3. 认证协议

遗留风险：自造 OAuth2 风格端点、Token 表和 Grant，却缺少标准兼容、安全升级与生态工具。

Ainer：

- Spring Security + Spring Authorization Server；
- Authorization Code + PKCE、Client Credentials、Refresh Token、Device Code、Token Exchange 按需采用；
- OIDC Discovery、JWK、UserInfo、Revocation、Introspection 按产品形态启用；
- 不把 password grant 作为标准能力；
- 短信、微信、企微等登录通过认证编排或独立扩展授权设计。

## 4. 模型与分层

遗留风险：Controller/Service/DAL 全局横切，VO 后缀激增，Entity/DTO/VO 大量复制，框架注解泄漏到所有层。

Ainer：

- feature-first；
- feature 内划分 api/application/domain/infrastructure；
- HTTP 模型、应用模型、领域模型和持久化模型按职责决定是否分离；
- DTO 显式设计，MapStruct 仅生成映射实现；
- 简单 CRUD 不机械套用复杂 DDD 模板，复杂度由规则证明。

## 5. 模块边界

遗留风险：`system`、`common`、`util` 成为无边界垃圾桶，所有模块都依赖它们。

Ainer：

- framework 只提供通用技术能力；
- identity、workspace、AI runtime 等拥有明确业务职责；
- 业务能力找不到归属时先澄清领域，不默认放入 kernel/common；
- Maven 依赖与 ArchUnit 同时检查边界。

## 6. 错误模型

遗留风险：手工数字段位冲突、常量散落，或者业务错误全部映射为 HTTP 200。

Ainer：

- 稳定字符串错误码：`AINER.<MODULE>.<ERROR>`；
- `ErrorCodeRegistry` 在启动期检查重复；
- HTTP status 表达传输语义；
- 响应体提供业务码、可安全展示消息和 request ID；
- 未知错误不泄露内部异常。

## 7. 安全与加密

遗留风险：内部调用只依赖可伪造 Header；密钥经过可逆混淆后写入源码；CBC 使用固定 IV；解密失败返回原值。

Ainer：

- 服务身份使用签名 Token、mTLS 或平台工作负载身份；
- Header 只能传播上下文，不能独立证明身份；
- 密钥保存在 KMS/Vault，使用 key version 与轮换；
- 字段加密使用认证加密和唯一 nonce；
- 解密/完整性校验失败必须显式失败并审计。

## 8. 演进方式

Ainer 采用
[演进式模块化平台架构](../decisions/0024-evolutionary-modular-platform-architecture.md)，不追求
一次性推翻并重写全部 xiaoqu。迁移采用渐进切片：

1. 用 characterization tests 固化旧行为；
2. 在 Ainer 重建设计清晰的垂直能力；
3. 通过适配器或稳定 API 并行接入；
4. 小流量验证、数据对账、可回退；
5. 调用方归零后删除旧实现。

最终目标是持续减少遗留债务，而不是先制造一个多年无法上线的新平台分支。
