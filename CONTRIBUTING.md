# 参与 Ainer Boot 开发

感谢参与 Ainer Boot。项目采用 clean-room 自主实现，目标是形成可长期维护、可独立授权和可商业交付的 AI 原生企业应用底座。源码按 [MIT License](LICENSE) 许可（ADR-0051）；MIT 不授予 Ainer 商标权。

## 开始之前

请先阅读：

1. [`AGENTS.md`](AGENTS.md)
2. [`docs/00-overview.md`](docs/00-overview.md)
3. [`docs/project-status.md`](docs/project-status.md)
4. [`docs/development.md`](docs/development.md)
5. [`docs/conventions.md`](docs/conventions.md)

架构、安全、数据归属、外部协议或许可证发生变化时，还必须阅读相关 ADR。

Ainer 的生产者构建要求 JDK 25，并统一使用锁定 Maven 4.0.0-rc-6 preview 的
`./mvnw`。系统 Maven 3.9+ 只用于 `scripts/verify-maven-consumers.sh` 的下游兼容验证。

## 开发原则

- 禁止复制商业竞品或许可证不兼容项目的源码、注释、模板和专有命名。
- 先定义模块归属、契约、事务和验收行为，再写基础设施实现。
- 业务模块不能直接依赖另一个业务模块的 Service 实现或数据库表。
- tenant 和 subject 只能来自可信身份上下文，不能来自外部自声明请求头。
- 新依赖必须记录版本、用途、许可证和替代方案，更新 `docs/dependencies.md`。
- 新配置必须具有安全默认值，并更新 `docs/configuration.md`。

## 标准工作流

```bash
git status --short --branch
git log --oneline --decorate -12
./mvnw --version
./mvnw clean verify
```

1. 确认工作区现状，保留他人未提交改动。
2. 从测试或可验证验收行为开始定义改动。
3. 小步实现，只修改任务范围内文件。
4. 更新相关代码、migration、测试和文档。
5. 执行受影响模块的 `./mvnw test` 或 `verify`，再执行完整 `./mvnw clean verify`。
6. 检查 `git diff --check` 和最终文件范围。

详细命令见 [`docs/development.md`](docs/development.md) 和 [`docs/testing.md`](docs/testing.md)。

## 提交要求

提交消息使用：

```text
type(scope): 中文描述
```

常用类型包括 `feat`、`fix`、`refactor`、`test`、`docs`、`build` 和 `chore`。一次提交应表达一个可审查的意图，不得混入生成文件、秘密或无关格式化。

提交或合并说明至少包含：

- 解决的问题和选择的边界；
- 用户可见或兼容性影响；
- 数据库与安全影响；
- 实际执行的验证命令及跳过项；
- 对应文档和 ADR；
- 已知限制与后续工作。

## 完成定义

一项工作只有在代码、测试、文档和迁移保持一致，真实失败语义得到验证，且未把未完成能力描述成已交付时才算完成。发布还需满足 [`docs/releasing.md`](docs/releasing.md) 的额外门禁。
