# ADR-0053：Initializer 已有项目增量接入与授权组合基线

- 状态：Accepted
- 日期：2026-08-28
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无
- 修订：保持 ADR-0035/0036 的 Manifest v1、ADR-0052 的 Manifest v2 安全边界；本 ADR
  增加已有项目接入方式，并修复 Workspace 与通用 Authorization 同时装配时的组合缺口

## 背景

`v1.3.0` 已能从空目录生成 Manifest v2 `simple-service + workspace` 安全纵向切片，但首个真实
产品消费者 `xq-platform-next` 不是空项目。接入“找货需求”时实际发生了四类手工工作：把生成的
`V1` migration 改为已有序列后的 `V3`，挑选并复制实体切片文件，补齐 POM 依赖与 compiler
parameter，以及在宿主已经装配 Authorization 时为 `workspace.read` / `workspace.write` 手工扩充
权限目录、scope 天花板和领域策略。

最后一项不是消费者业务差异。Workspace Controller 已声明 `@AinerAuthorize`，但 Workspace 模块
没有向通用授权引擎贡献自己的粗门禁策略；宿主产品的完整策略不认识 workspace 权限时，请求会在
进入已有的 ACTIVE membership 校验前被 `UNKNOWN_POLICY` 拒绝。继续要求每个消费者复制同一段
策略，会造成安全语义漂移。

另一方面，允许生成器自动猜测 Flyway 版本、覆盖已有 Java/POM/migration 或根据目录内容静默选择
策略，都不满足 Initializer 的确定性和失败关闭合同。

## 决策驱动因素

- 已有项目接入必须默认不覆盖、不删除，重复执行必须幂等；
- Flyway 版本属于消费者数据库历史，不能由脚手架猜测；
- POM 只能做结构可验证、范围有限的加性合并；
- Workspace 自己拥有通用粗门禁贡献，产品仍拥有其业务权限策略；
- scope 粗门禁不能取代 ACTIVE membership、OWNER/ADMIN 和对象归属检查；
- Manifest v1 与 `init` / `preview` / `diff` 既有合同保持不变。

## 备选方案

### 方案 A：继续要求消费者手工复制和改名

无需新增工具，但 migration、依赖和授权策略的重复劳动已经由真实消费者证明，且手工结果难以重放。
放弃。

### 方案 B：自动扫描并选择下一个 Flyway 版本，强制覆盖冲突文件

体验看似更快，但 Flyway 版本可能使用整数、日期或其他受控序列；扫描最大值不能证明新的版本符合
消费者发布历史。覆盖也会破坏用户 WIP。放弃。

### 方案 C：新增显式增量命令与模块级授权策略贡献

Manifest 继续描述产品切片，命令显式携带目标项目和 migration 起始版本；生成器只新增文件并有限
合并 POM。Authorization 保留宿主完整策略优先级，模块贡献只填补宿主未认领的权限。采用。

## 决策

### 1. CLI 合同

新增两个只接受 Manifest v2 的命令：

```text
plan-add <manifest.yaml> <target-dir> --migration-version N
add      <manifest.yaml> <target-dir> --migration-version N
```

`N` 必须是正整数，并作为第一个实体的 Flyway 版本；多实体按 manifest 顺序依次递增。目标中任意
同版本 migration 已存在时失败，只有同路径、同字节的上次生成结果可作为幂等重放。命令不自动
选择、改写或删除 migration。

`plan-add` 完成全部只读预检并报告新增/不变文件、POM 缺失依赖和 compiler parameter，不写盘。
`add` 复用同一计划后执行。Manifest v1 调用这两个命令立即失败。

### 2. 已有项目预检与写入边界

首版只支持单模块 Maven/Spring Boot 项目：

- 顶层必须存在安全可解析的 `pom.xml`，并以 `type=pom`、`scope=import` 导入与 manifest
  `ainner` 字段完全一致的 `dev.ainer:ainer-dependencies`；
- `src/main/java` 中必须存在 `@SpringBootApplication`，manifest package 必须位于其默认组件扫描
  范围；
- POM 只向顶层 `<dependencies>` 添加安全切片缺失的依赖，并补
  `maven.compiler.parameters=true`；依赖 scope 比所需范围更窄时失败；
- 生成 Java、测试、配置和 migration 只允许“新路径”或“同路径同字节”；不同内容、目录占位或
  执行位漂移全部在写入前拒绝；
- 不修改宿主 Application、`application.yml`、README、Wrapper 或任何外部文件。

增量切片生成一个位于 manifest package 下的装配配置，通过宿主组件扫描导入
`WorkspaceModuleConfiguration`；生成的 Mapper 自带 `@Mapper`，由宿主既有扫描配置或 MyBatis
自动配置登记，避免重复扫描。POM 同时确保 Workspace、Authorization、Security、Persistence、
Validation、OpenAPI、PostgreSQL 与测试依赖存在。

### 3. Authorization 组合合同

通用授权模块新增 `AuthorizationPolicyContributor`。组合规则：

1. 宿主 `DomainAuthorizationPolicy` 已为某 permission 返回 GrantPath 时，宿主同时拥有该权限的
   scope、关系和资源状态语义；模块贡献不能扩大或替换；
2. 宿主未认领时，可以由恰好一个模块贡献者提供完整 scope + domain 策略；
3. 多个模块贡献者认领同一 permission 时抛出配置冲突，失败关闭；
4. Spring 配置顺序导致默认 deny-all 占位与宿主 bean 同时存在时，组合器显式忽略占位；多个真实
   宿主策略只有在 Spring 能以唯一 `@Primary` 选出宿主策略时才接受，否则视为错误。

Workspace 模块贡献 `workspace.read`、`workspace.write`、`workspace.audit.read` 的权限元数据与
同名 scope 天花板，GrantPath 使用 `RELATION_DERIVED` 作为 HTTP 粗闸门。该关系只说明“继续进入
Workspace 应用服务”，不声明实际成员关系；ACTIVE membership、OWNER/ADMIN、对象归属与审计仍
由 `WorkspaceApplicationService` 重新校验。创建 Workspace 时对象尚不存在，因此不得预先要求
Workspace Binding。

Manifest v2 新项目从本修订起显式装配 Authorization + Workspace；已有项目的 `add` 通过 POM 与
增量配置完成同一组合。

## 后果

### 正面

- 真实消费者不再手工重命名 migration、拼 POM 或复制 workspace 授权策略；
- 相同 manifest、目标状态和显式 migration 版本可重放，第二次 `add` 为零新增；
- 产品策略继续优先，模块只能填补产品未认领的权限，避免隐式提权；
- 新项目与已有项目都实际启用 Authorization 粗门禁，同时保留应用服务对象级失败关闭。

### 负面与限制

- 首版不支持多模块 reactor、Gradle、Java AST 修改、自动菜单/UI、自动删除或 rollback；
- POM 合并只处理顶层依赖和 compiler parameter，不替消费者决定 plugin、profile 或仓库策略；
- migration 版本必须由调用者显式选择，工具不会提供“最大值加一”的便利猜测；
- 新 SPI 是加性 Java 合同；现有完整策略 bean 保持可用，多个完整宿主策略必须有唯一 `@Primary`。

该变更增加公开 CLI 功能与 Java SPI，按 ADR-0045 必须进入下一个 minor `1.4.0`，不得包装为
`1.3.x` patch；`1.3.0` 的既有制品、tag 与 Release 保持不可变。

## 安全、数据与运维

XML 解析禁止 DOCTYPE、外部 DTD 和外部 schema。POM 在计划后若发生变化，写入时拒绝，避免并发
覆盖。临时 POM 使用同目录原子替换；生成器不执行 migration、不连接数据库、不启动应用，也不
读取凭据。所有真实数据库和 JWT 语义继续由独立消费者 Testcontainers 门禁验证。

## 验收记录

- Authorization 组合单元测试覆盖模块补位、宿主优先和重复贡献冲突；
- Workspace 真 RSA JWT HTTP 测试同时装配 Authorization、Workspace 与只认领产品权限的宿主
  策略，验证 401、缺 scope 403、创建/读取成功和 outsider 404；
- Initializer/Core/CLI 测试覆盖显式 V3、只读 plan、POM 有限合并、重复 add 零新增和 migration
  冲突零写入；
- `verify-initializer-consumer.sh` 增加 existing-project 通道：保留已有项目全部字节，在 V1/V2 后
  生成 V3，重复 add 幂等，并以真实 PostgreSQL 18.3 + JWT 执行两套安全 CRUD；
- 首个真实消费者复验与完整 reactor 数量以 `docs/project-status.md` 的当次验证记录为准。

## 参考

- [ADR-0035](0035-project-initializer-and-manifest-v1-baseline.md)
- [ADR-0036](0036-initializer-crud-generation.md)
- [ADR-0037](0037-post-greenfield-authorization-baseline.md)
- [ADR-0045](0045-versioning-lts-and-patch-baseline.md)
- [ADR-0052](0052-initializer-v2-secure-vertical-slice.md)
- [`docs/design/ainer-scaffold-design.md`](../design/ainer-scaffold-design.md)
