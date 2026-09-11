# ADR-0056：端点层默认拒绝与端点访问声明

- 状态：Accepted
- 日期：2026-09-11
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无
- 修订：ADR-0037 §4 与 ADR-0052 §4 的**执行细节**——`@AinerAuthorize` 仍是低/中风险端点的粗粒度
  闸门、生成工程仍只公开健康检查；本 ADR 补上两者共同缺失的那一层：没有声明的 handler 不再落到
  `anyRequest().authenticated()`

## 背景

`@AinerAuthorize`（ADR-0037 §4）是**逐方法可选**注解，由 MVC 拦截器 `AinerAuthorizeInterceptor`
消费。没有该注解的 handler 在 `AinerRequestAuthorizationManager` 里返回 `null`，最终落到 Spring
Security 的 `anyRequest().authenticated()`——**只要求登录，不要求任何权限**。

后果是「默认拒绝」只成立于决策引擎内部（未知权限 / 无策略 / 无 Binding → DENY），在**端点层不成立**：

- 新增一个 Controller 方法若漏写注解，它对**全部已认证主体**开放；
- 编译期、启动期、既有 CI 都不会失败——这套组合此前没有任何一层会报错。

对照：产品侧仓库（xiaoqu-platform）用 `script/shell/check-preauth.sh` + pre-commit + 部署阶段扫描
三重兜底防同类「裸奔接口」（历史上因此出过 14 个裸奔接口的事故）；本仓没有对应物。

2026-09-11 的实测确认了这个缺口可被静态枚举：参考装配（`ainer-server` 及其依赖模块）共 106 个
handler，其中 19 个既没有 `@AinerAuthorize` 也没有任何声明（平台信息端点、两个 `/internal/**`
控制面、通用授权管理面 14 个；另有 Initializer v2 模板 6 个）。

## 决策驱动因素

- **缺口在默认值，不在能力**：决策引擎的默认拒绝已经成立；出问题的是「没写声明时的默认行为」。
  修默认值比要求每个宿主自查更可靠——正是「不写就不会错」的默认值造成了这次缺陷。
- **默认值不能交给宿主自觉**：把安全默认值留在宽容侧、再由每个消费者自己想起来加固，等于把
  安全基线交给运气。产品仓库的前车之鉴说明「约定存在但没有机器执行」必然衰减。
- **声明必须落在源码里**：配置文件和脚本白名单都在代码之外，review 新端点的人看不到它们；注解
  在 diff 里、在 IDE 里、在编译产物里。
- **不能为了变绿放宽公开面**：任何「为了通过门禁」的临时豁免都会立刻变成永久豁免，因此豁免必须
  逐条写理由、必须能被追问到具体强制机制。
- **升级不能无声**：这是一次行为变更，消费者需要明确的迁移路径与灰度开关，而不是「升级后自己发现
  接口 403 了」。

## 备选方案

### 方案 A：默认 `WARN`（保留旧行为，只记日志）

零破坏性、升级平滑，但**不配置任何东西的宿主仍然带着完整缺口**——静态门禁只保护本仓，保护不了
消费方。本 ADR 要修的正是「默认放行」，把默认值留在 `WARN` 等于不修。不采用。

### 方案 B：只加静态门禁，不改运行期

能把新端点漏声明挡在 CI，但无法覆盖：脚本扫描范围之外的模块、第三方生成的端点、运行期被
`public-paths` 误配放行的路径、以及已经存在但没被扫到的形态。且门禁可以被删掉或绕过（脚本是
仓库文件），运行期默认值不能。作为**第二层**保留，不作为唯一层。不采用（单层形态）。

### 方案 C：运行期 `FAIL_CLOSED` 默认 + 源码显式声明 + 静态门禁（选中）

三层同一条不变量：决策引擎默认拒绝（已有）+ 端点层未声明即 403（新增，默认）+ 静态门禁硬失败
（新增）。任一层单独失效时，另一层仍然拦得住。

## 决策

### 1. 端点层默认拒绝

`AinerAuthorizeInterceptor` 对 handler 分四类处置：

| handler | 处置 |
|---|---|
| 有 `@AinerAuthorize` | 走 Ainer 决策引擎（原有路径，行为不变） |
| 有 `@EndpointAccess` | 按声明的访问类别放行（见 §2） |
| 第三方 jar 提供的 handler | 由外层 Resource Server 链负责（见 §5） |
| 其余（宿主自己的 Controller 但没声明） | 按 `ainer.security.endpoint-authorization.mode` 处置，**默认 `fail-closed`**：直接 403 并记 ERROR 日志（含 HTTP 方法与端点身份） |

配置键与默认值：

| 配置 key | 默认 | 说明 |
|---|---|---|
| `ainer.security.endpoint-authorization.mode` | `fail-closed` | 未声明端点处置；`warn` 仅作升级期灰度 |
| `ainer.security.endpoint-authorization.framework-handler-packages` | `org.springframework.`、`org.springdoc.`、`io.swagger.` | 第三方 handler 包前缀，宿主可覆盖（见 §5） |

默认值写在代码里（`EndpointAuthorizationProperties.DEFAULT_MODE`），不写进任何 profile 的 YAML，
三个 profile 一律继承；这样「默认拒绝」不会因为某个 profile 漏配而消失。

### 2. 两套声明机制的分工

| 机制 | 声明的东西 | 适用 |
|---|---|---|
| `@AinerAuthorize(permission = "...")` | **权限语义**（稳定 PermissionCode，进决策引擎） | 有权限语义的业务端点 |
| `@EndpointAccess(kind, reason)` | **访问类别**（不需要权限码时的显式口径） | 匿名 / 仅登录 / 委托授权 |

`@EndpointAccess.kind` 三值：`PUBLIC`（匿名）、`AUTHENTICATED`（只要求已认证）、`DELEGATED`（HTTP
层不设 Ainer 权限闸门，授权由应用服务或该端点专属的安全构件强制；`AUTHENTICATED` 与 `DELEGATED`
都仍要求非匿名认证）。`reason` 必填：静态门禁对缺失 reason 判违规，运行期另记 WARN。

**为什么做成一个带 `kind` 的注解，而不是 `@PublicEndpoint` 等两个平行注解**：三类口径互斥、
共用同一段解析与放行逻辑（都要走「已声明 → 放行 / 未声明 → 默认拒绝」这一条判断），拆成多个注解会
把同一处逻辑摊到多个解析分支上，也让「哪些端点未声明」这个枚举失去单一入口；`@AinerAuthorize`
自己的 `accessMode()` 也是同一形态（注解 + 枚举），保持一致。名称上 `@EndpointAccess` 读作「该端点
的访问口径」，涵盖三类而不暗示只有匿名。

**作用域差异是有意的**：`@AinerAuthorize` 只支持方法级（`@Target(ElementType.METHOD)`，类级写法
编译期即失败）——权限码必须逐个方法审阅；`@EndpointAccess` 支持类级——「这个 Controller 整体只需
登录 / 授权在服务里做」这类结论天然是类级的。

### 3. 与 `public-paths` 的关系

`ainer.security.resource-server.public-paths` 是**外层 filter chain 白名单**（Spring Security 最先
执行），`@EndpointAccess` 是**源码侧声明**（MVC 拦截器在 handler 解析后消费）。两者不可互相替代，
实测出的行为是非对称的：

| `public-paths` | `@EndpointAccess(PUBLIC)` | 结果（实测） |
|---|---|---|
| 有 | 有 | 匿名可达（`/api/platform/info` 即此形态，真 HTTP 200） |
| 有 | 无 | 运行期 **403** + 静态门禁**失败**——不允许「只改配置就上线匿名端点」 |
| 无 | 有 | 匿名 **401**（外层链仍要求认证），失败关闭方向；审计上视为声明与事实不一致 |
| 无 | 无 | 匿名 401；已认证主体 **403**（缺声明） |

因此「真正匿名」必须两处同时登记：白名单给可达性，注解给可审计性与运行期放行。

### 4. 逃逸舱与兼容策略（破坏性变更）

- **`mode = warn`**：未声明端点恢复旧行为（只要求已认证），但每次访问记 WARN 日志。语义与代价：
  它是**升级期灰度开关**，不是等价替代——处于 `warn` 的宿主等于自愿保留本 ADR 要修的缺口，因此
  日志必须被当成待办清单（按端点身份批量补声明），补齐后切回 `fail-closed`。
  **静态门禁不受该开关影响**，始终硬失败。
- **白名单 `scripts/endpoint-authorization-whitelist.txt`**：`类#方法`（支持 `*` 通配）+ **必填理由**，
  只豁免静态门禁、**不改变运行期裁决**（登记项在 `fail-closed` 下照样 403，除非它由外层安全链或
  应用服务自己完成鉴权）。无匹配 handler 的过期登记失败关闭，防止白名单烂成永久豁免。它只服务
  「运行期拦截器覆盖不到」的场景，不是让新端点免于声明的捷径。
- **迁移路径**：① 补声明（推荐）；② 显式配置 `mode: warn` 灰度，按 WARN 日志补齐后切回。
  Changelog 按破坏性变更记录，`docs/security.md` §3.4.3 给出配置矩阵与两条路径。

### 5. 第三方 handler 豁免面

Spring Boot 错误分发（`/error`）、Actuator 端点、springdoc 文档端点不是宿主的 Controller，拿不到
注解也无法逐个审阅，其认证由外层链负责（ADR-0052 要求 `/v3/api-docs` 需有效 JWT，本配置**不会**
让它匿名——实测：带真签名 JWT 200、无 token 401）。豁免面由
`framework-handler-packages` 显式给出，**宿主可覆盖**（覆盖即整体替换，不做增量追加），例如：

```yaml
ainer:
  security:
    endpoint-authorization:
      mode: fail-closed
      framework-handler-packages:
        - org.springframework.
        - org.springdoc.
        - io.swagger.
        - com.acme.platform.docs.   # 宿主引入的其他第三方 MVC 库
```

配置该 key 时默认前缀不再自动保留，需要一并写出；这是有意的——豁免面必须是显式清单，不能靠
「默认值还在」隐式继承。

## 后果

### 正面

- 「默认拒绝」在端点层第一次成立：漏写声明 = 403 + ERROR 日志 + CI 失败，而不是静默对全部已认证
  主体开放。
- 声明的理由（`reason`）留在源码里，评审与审计都有可追问的依据；`DELEGATED` 必须指向具体强制机制。
- 参考装配 106 个 handler 全部显式分类，真实树零违规；三层防护中任一层被绕过仍有另外两层。

### 负面与风险

- **破坏性变更**：既有消费者未声明的端点会从「已认证即可访问」变为 403。缓解：显式 `warn` 灰度、
  Changelog 与文档写清迁移路径。
- **注解可见性成本**：每个新端点都要多写一行声明。这是本次决策愿意付的代价——静默默认值已经用一次
  真实缺口证明更贵。
- **豁免面被滥用**：`framework-handler-packages` 与白名单都可能被写宽。缓解：白名单理由必填 + 过期
  登记失败关闭；豁免面在文档与 ADR 里被点名为「显式清单」，扩张需要评审。
- **运行期与静态两层信息不同步**（例如 `@EndpointAccess(PUBLIC)` 但没登记 `public-paths`）：运行期
  失败关闭（401），审计上要靠人工/测试发现；当前由 `docs/security.md` §3.4.2 的表格与运行期测试
  固定语义。

## 安全、数据与隐私

- 未声明端点默认 403，不在响应体里暴露端点身份或内部决策；端点身份只进服务端 ERROR 日志（供告警
  与定位），不返回给客户端。
- 匿名端点仍需两处登记，且 `reason` 必须写清数据面（例如 `/api/platform/info` 只返回产品名、运行
  模式与 JDK feature 版本，不含租户/用户数据）。
- `DELEGATED` 不降低被委托方的要求：参考装配的三处分别由 `GrantAdministrationGuard`（含应用服务
  事务边界二次校验）、受信导出主体 + `SCOPE_workspace.audit.export.all`、`SCOPE_workspace.owner-recovery.*`
  强制，都不是「免检」。

## 运维与迁移

- CI：`JDK 25 / Maven 4 quality gate` job 新增独立 step `Verify endpoint authorization declarations`
  （不改动既有 job/step 的 `name`）；本地 `scripts/check-release-contracts.sh` 一并执行。
- 运行期排障：`grep '端点未声明授权口径'` 可枚举被拒端点（`FAIL_CLOSED` 记 ERROR、`WARN` 记 WARN，
  两者都带 HTTP 方法与 `类#方法`）。
- 升级顺序建议：先在非生产环境打开默认值，收 ERROR 日志 → 补声明 → 再上生产；确需灰度时用
  `mode: warn` 并跟踪该日志。

## 未解决与后续

1. **Authorization Server 的 14 个 handler 走白名单而非注解**：该应用（`ainer-authorization-server`）
   不依赖 `ainer-module-authorization`，既拿不到注解类，也不装配 `AinerAuthorizeInterceptor`；它的
   端点由自己的 `SecurityFilterChain` 按路径精确强制（`/internal/**` 要求专用 SERVICE scope 且未列出
   path `denyAll`、`/api/me/access-token-revocations` 已认证 + ACTIVE token 过滤器、`/login` 显式
   permitAll）。把它改成内联声明的前提是：为该应用引入 `ainer-module-authorization` 依赖并装配拦截器
   （或把注解类型下沉到更低的公共模块），同时逐个复核上述链的语义不被双重闸门覆盖——在出现第二个
   消费者或该应用需要统一声明口径之前不做。
2. **`src/test` 夹具不进静态门禁**：有意保留——未声明探针本身就是「运行期第二层兜底」的回归证据
   （见验收记录），扫测试源码会让负向夹具反过来被判违规。是否追加「夹具可达性」检查（例如扫描
   import 了 `AuthorizationModuleConfiguration` 的测试文件）**不做**：可达性判定需要解析注入与配置
   条件，误报率高、收益不成比例；本次全量 `clean verify` 已经用真实失败暴露过两个受影响的夹具，
   由测试本身承担这层守卫。
3. **静态门禁的解析边界**：不解析继承来的映射（基类 Controller、接口默认实现）、不解析第三方 jar
   内端点；嵌套 `static Controller` 已支持（按花括号深度维护类型作用域栈）。出现新形态时先扩门禁再
   上线，不静默放行。
4. **`framework-handler-packages` 的默认清单**目前覆盖 Spring Boot / springdoc / swagger；宿主引入
   其他第三方 MVC 库时需自行覆盖（见 §5）。是否需要按「已知库清单」随版本演进，等真实消费者出现再定。

## 验收记录

2026-09-11，JDK 25.0.2 + Maven 4.0.0-rc-6 + Colima/PostgreSQL 18.3：

- 静态门禁：真实树 672 个 Java 文件、106 个 handler（`@AinerAuthorize` 68、方法级 `@EndpointAccess` 2、
  类级 22、白名单 14），**违规 0 处**。
- 负向实测：临时新增无注解端点 → 门禁 exit 1 并打印 `文件:行`；临时登记白名单 → exit 0（机制可用，
  随后删除并核对 sha256 与 HEAD 一致）；运行期打该端点 → **403** 且拦截器打印
  `端点未声明授权口径，FAIL_CLOSED 拒绝：GET /api/platform/info-probe -> …PlatformInfoController#probe`；
  还原后文件 sha256 与改动前逐一相同。
- 运行期测试（真 HTTP + 真签名 JWT + PostgreSQL Testcontainers，无 Mockito/H2）：未声明端点已认证 403 /
  匿名 401、`@AinerAuthorize` 无 Binding 403 建 Binding 后 200、`PUBLIC` 匿名 200、`AUTHENTICATED` 与
  类级 `DELEGATED` 匿名 401 已认证 200、`public-paths` 的 `/api/platform/info` 匿名 200、第三方 handler
  （`/actuator/health`、`/v3/api-docs`、错误分发 404）不被误拒；`warn` 模式放行但留 WARN 日志。
- 全量：`./mvnw clean verify` → 28/28 模块 BUILD SUCCESS、**624 tests / 0 failure / 0 error / 0 skipped**、
  4m21s（同环境基线 604 tests，用时 4m18s）。
- 详细过程与原始输出见 [`../project-status.md`](../project-status.md) §3 的 2026-09-11 条目。

## 参考

- ADR-0037：通用授权（`@AinerAuthorize` 端点门禁、决策引擎、Binding/策略组合）
- ADR-0030 §8.4：高价值写操作必须在应用服务中显式授权（`DELEGATED` 的依据）
- ADR-0052 §3/§4：Initializer v2 生成工程的安全口径与受保护 OpenAPI
- [`../security.md`](../security.md) §3.4：端点访问声明与端点层默认拒绝（规范表述、配置矩阵、逐端点处置表）
- [`../conventions.md`](../conventions.md) §9、§13.3：新增/修改 Controller 方法的规则与门禁执行面
- [`../api.md`](../api.md) §3、§8：公开端点如何声明、端点门禁的归类要求
- [`../../scripts/check-endpoint-authorization.sh`](../../scripts/check-endpoint-authorization.sh)、
  [`../../scripts/endpoint-authorization-whitelist.txt`](../../scripts/endpoint-authorization-whitelist.txt)
