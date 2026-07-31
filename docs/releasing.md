# Ainer 版本与发布规范

> 文档类型：长期维护规范 · 状态：基础版 · 最近核对：2026-07-30 · 适用版本：`0.1.x`

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
4. JDK 25 下的 `./mvnw clean verify` 通过，PostgreSQL Testcontainers 没有因 Docker 缺失跳过；
5. `scripts/verify-maven-consumers.sh` 证明 Maven 4 与 Maven 3.9+ 下游均可消费制品，14 个标准
   Consumer POM 中的 `${revision}` 都有当前安装版本属性可解析，`ainer-spring` JAR 包含
   Spring 配置元数据；
6. Maven Artifact Plugin 的 `check-buildplan` 和两次构建 `compare` 均通过；
7. 两个可执行 JAR 构建并在目标 JDK 启动；
8. 空库 migration、升级 migration、关键约束和事务回滚已验证；
9. issuer、audience、密钥、secret 注入和 Actuator exposure 已审查；
10. 401/403、Workspace 跨租户、AI 预算/脱敏等安全 smoke test 通过；
11. 备份恢复和应用回滚步骤在非生产环境演练；
12. 文档链接、命令和版本号已核对。

## 4. 构建与记录

```bash
export AINER_VERSION='<目标版本>'
./mvnw --version
./mvnw -Drevision="$AINER_VERSION" artifact:check-buildplan
AINER_REPRO_REPOSITORY="$(mktemp -d)"
./mvnw -Drevision="$AINER_VERSION" -Dmaven.repo.local="$AINER_REPRO_REPOSITORY" clean install
./mvnw -Drevision="$AINER_VERSION" -Dmaven.repo.local="$AINER_REPRO_REPOSITORY" clean verify artifact:compare
./scripts/verify-maven-consumers.sh
git diff --check
git status --short --branch
```

生产者构建、安装和发布必须使用锁定 Maven 4.0.0-rc-6 preview 的 Wrapper，不能用全局 Maven
替代。系统 Maven 3.9+ 只由 consumer 脚本验证下游兼容。`install` 在这里仅用于建立可重复构建
参考和 golden consumer，不属于日常开发命令；两次构建应使用隔离的本地仓库。开发快照可以
省略 `AINER_VERSION`，脚本会读取根 POM 的 `revision`；发布候选必须显式传入目标版本。

发布记录至少保存：源码 commit、版本、构建 JDK/Maven、依赖锁定结果、测试摘要、consumer 与
可重复构建结果、数据库验证、制品 checksum、部署环境和批准人。候选 GitHub Actions 工作流已经
编排 JDK 25、Maven 4、Docker、`skipped=0`、consumer 与短期 SBOM 门禁，但在 Maven 4 RC6
官方发行包可下载并首次完整成功前不能称为正式 CI。制品发布、签名与 provenance 管线仍未实现。

## 5. 数据库发布

- 发布前从备份副本验证升级，不在生产第一次执行；
- 评估 DDL 锁、回填时长、磁盘增长和上一版应用兼容性；
- 破坏性变更使用 expand-contract；
- migration 成功但应用失败时，优先回滚到仍兼容 schema 的应用版本；
- 不依赖自动 down migration，不修改 Flyway history 伪造恢复。

## 6. 回滚与停止条件

遇到 migration 不一致、鉴权绕过、跨 tenant 数据暴露、秘密泄露、不可解释的数据损坏或无法恢复的事件积压时立即停止发布。回滚前先保存诊断资料，再按已演练方案回退应用或恢复数据库。

如果新 migration 已提交且不向后兼容，不能只回滚 JAR；必须执行该版本事先定义的恢复方案。

## 7. 发布后

- 验证 health、Token 签发、JWT 验证和关键业务 smoke；
- 观察错误率、延迟、数据库连接/锁、outbox 积压和 AI provider 失败；
- 将实际结果写入发布记录；
- 清空 Changelog 的 `Unreleased` 时立即建立新的空区段；
- 发现设计偏差时新增 ADR 或修复文档，不让口头结论成为长期规则。
