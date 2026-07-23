# Ainer 版本与发布规范

> 文档类型：长期维护规范 · 状态：基础版 · 最近核对：2026-07-23 · 适用版本：`0.1.x`

## 1. 版本策略

Ainer 使用语义化版本：`MAJOR.MINOR.PATCH`。当前工程版本为 `0.1.0-SNAPSHOT`，表示 API、模块边界和数据库仍处于快速演进期。

- `PATCH`：兼容性修复、文档或内部优化；
- `MINOR`：向后兼容的新能力；在 `0.x` 阶段也可能包含明确记录的边界调整；
- `MAJOR`：稳定期后的破坏性 API、配置或数据契约变化。

预发布可以使用 `-alpha.N`、`-beta.N`、`-rc.N`。正式发布不得包含 `SNAPSHOT`。

## 2. 兼容性维度

发布说明必须分别评估：Java API、HTTP API、错误码、JWT claims/scopes、配置键、数据库 schema/migration、事件 payload、Starter 自动配置、商业授权和运维方式。某一维度兼容不代表整体兼容。

事件 payload 必须带版本；消费者按事件 ID 幂等。配置或 API 弃用应至少跨一个计划发布窗口，并在 Changelog 提供替代方案。

## 3. 发布候选门禁

1. 里程碑范围和未完成项已更新到 `project-status.md`；
2. `CHANGELOG.md` 的 `Unreleased` 已整理到目标版本；
3. 所有相关 ADR 已接受，许可证台账已更新；
4. `mvn clean test` 通过，PostgreSQL Testcontainers 没有因 Docker 缺失跳过；
5. 两个可执行 JAR 构建并在目标 JDK 启动；
6. 空库 migration、升级 migration、关键约束和事务回滚已验证；
7. issuer、audience、密钥、secret 注入和 Actuator exposure 已审查；
8. 401/403、Workspace 跨租户、AI 预算/脱敏等安全 smoke test 通过；
9. 备份恢复和应用回滚步骤在非生产环境演练；
10. 文档链接、命令和版本号已核对。

## 4. 构建与记录

```bash
mvn clean package
git diff --check
git status --short --branch
```

发布记录至少保存：源码 commit、版本、构建 JDK/Maven、依赖锁定结果、测试摘要、数据库验证、制品 checksum、部署环境和批准人。未来自动化管线应生成并签名这些 provenance；当前尚未实现制品发布和签名管线。

## 5. 数据库发布

- 发布前从备份副本验证升级，不在生产第一次执行；
- 评估 DDL 锁、回填时长、磁盘增长和上一版应用兼容性；
- 破坏性变更使用 expand-contract；
- migration 成功但应用失败时，优先回滚到仍兼容 schema 的应用版本；
- 不依赖自动 down migration，不修改 Flyway history 伪造恢复。

## 6. 回滚与停止条件

遇到 migration 不一致、鉴权绕过、跨 tenant 数据暴露、秘密泄露、不可解释的数据损坏或无法恢复的事件积压时立即停止发布。回滚前先保存诊断证据，再按已演练方案回退应用或恢复数据库。

如果新 migration 已提交且不向后兼容，不能只回滚 JAR；必须执行该版本事先定义的恢复方案。

## 7. 发布后

- 验证 health、Token 签发、JWT 验证和关键业务 smoke；
- 观察错误率、延迟、数据库连接/锁、outbox 积压和 AI provider 失败；
- 将实际结果写入发布记录；
- 清空 Changelog 的 `Unreleased` 时立即建立新的空区段；
- 发现设计偏差时新增 ADR 或修复文档，不让口头结论成为长期规则。
