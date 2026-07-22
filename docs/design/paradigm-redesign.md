# Aurora 范式重新设计 · yudao 缺陷与改进

> 状态:**DRAFT v0.1** · 日期:2026-07-22
> 性质:**范式级重新设计**(不是兼容性升级)
> 前提变更:Aurora **不预设继承 yudao 范式**,从 bladex/yudao/dante-cloud/Snowy 四家择优 + 必要时引入新设计,主动改掉 yudao 的 7 个范式缺陷
> 迁移策略:xiaoqu 现有 ~6000 文件**全部重写,不妥协**
> 关联:`design/aurora-scaffold-design.md`(主设计)、`migration/aurora-migration-plan.md`(迁移)

---

## 0. 为什么推翻「100% 兼容 yudao」

前一版设计把「与 xiaoqu 现有 yudao 范式 100% 兼容」当成硬约束,理由是「6000 文件已跑在该范式上,改范式 = 返工」。**这个推理错了**:

- 它把「迁移成本」等同于「范式好坏」。迁移成本是要算的代价,可用「分批重写、strangler 替换」摊薄;但范式缺陷一旦固化进新脚手架,会**持续出血整个生命周期**。
- greenfield 的全部意义,就是**摆脱历史包袱,设计一个更对的范式**。让新脚手架迁就有缺陷的旧范式,等于浪费这次机会。
- yudao 范式有 7 个真实、可量化、可复现的设计缺陷(下文逐条核实)。把它们原样搬进 Aurora,等于让新平台从第一天就背上 yudao 的债。

**新前提**:Aurora 不以 yudao 为基线,而以「正确的工程范式」为基线。yudao 的好东西(如 BaseDO 审计字段、统一响应)可以借鉴,坏东西(下文 7 条)必须改掉。xiaoqu 迁移 = 按新范式重写,不是改包名搬家。

---

## 1. yudao 范式 7 缺陷(真实代码核实)

> 以下均基于真实代码统计(grep/git show),可复核。参考:yudao `origin/master-jdk25`、xiaoqu `~/01-code/xq/xiaoqu-platform`、Snowy `~/01-code/xq/Snowy`、dante `~/01-code/xq/dante-cloud`。

### 缺陷 1:循环依赖,靠 `@Lazy` 兜底

- **证据**:yudao `@Lazy` 出现 **297 次**;xiaoqu **110 次**。`application.yaml` 两套都开 `allow-circular-references: true`(注释直言"三层架构历史遗留")。
- **典型**:`ProductSpuServiceImpl` ↔ `ProductSkuServiceImpl` 互注 `@Lazy`(注释明写"循环依赖,避免报错");system 的 `OAuth2↔Permission↔Tenant`、bpm 的 Flowable 三层互注。
- **根因**:模块内 Service 之间直接 `@Resource` 互注,而非走契约/事件解耦。`api` 子包只解耦跨模块,模块内部仍是双向持有。
- **影响**:全平台 Service 启动/拆分/异步都踩代理未就绪的坑;是 yudao 难拆微服务的根因之一。

### 缺陷 2:数据权限粒度太粗(表维度)

- **证据**:yudao `DeptDataPermissionRule` 以 `Map<tableName, column>` 注册,`DataPermissionRuleHandler` 按 table 名重写 SQL 拼 WHERE。同一张 `system_user` 表,`/user/page`(应受限)和 `/user/list-all-simple`(下拉,应全可见)无法区分范围,只能整体 `@DataPermission(enable=false)` 打补丁。
- **根因**:权限钩子在 MyBatis-Plus 表层,没把"哪个接口/Mapper 方法"作为一等维度。
- **对比**:Snowy 的 API 维度 `DataScope{apiUrl, dataScope, scopeAll}` + 预计算表 + scopeKey 去重。

### 缺陷 3:认证 token 自造,非标准 OAuth2

- **证据**:yudao 自造 `system_oauth2_access_token`/`refresh_token` 表 + `OAuth2TokenCommonApi` + 手撸 `OAuth2GrantServiceImpl` + 自造 `/system/oauth2/token` 端点。`spring-authorization-server` 依赖**命中 0 处**。
- **缺失**:标准 grant types 协议、标准 endpoint、JWT/JWK、introspection(RFC 7662)、PKCE、OIDC、token revocation(RFC 7009)、passkey。`userInfo` 还塞进 `Map` 字段(不标准不安全)。
- **对比**:dante-cloud 直接基于 Spring Authorization Server,标准 endpoint/JWT/JWK 全套。

### 缺陷 4:命名与分层混乱

- **证据**:
  - controller/admin 与 controller/app 两套并存,但 `dal/` 顶层共享不按端切 → 分层轴不统一。
  - VO 后缀 7 种:yudao ReqVO **823**、RespVO **588**、PageReqVO **309**、SaveReqVO **227**、SimpleRespVO 24、ExportReqVO 3、ExcelVO 11。一个实体 CRUD 常拆 4 个 VO 类。
  - 实体三处放:`dal/dataobject/`(DO)、`dal/mysql/`(Mapper)、`api/dto/`(DTO)—— 同一概念三份近乎重复的字段类。
  - 包路径 6 层:`...controller.admin.tenant.vo.tenant.TenantPageReqVO`。
- **根因**:把"分层 + 端 + 领域 + 用途"四个正交维度全铺成包目录,再用后缀类名双标。

### 缺陷 5:模块边界不清(module-system 成"上帝模块")

- **证据**:xiaoqu `xq-module-system/service/` 下混入 `organization`(销售组织)、`project`(项目)、`payment`(支付)、`datascope`、`member`、`social` 等与"系统配置/权限/字典"无关的业务。被 **14 个模块**扇入(yudao 也是 14)。
- **根因**:无 "kernel(纯技术)vs business(业务域)" 切分,"无家可归"功能默认落 system。
- **影响**:改 system 任何接口/表/错误码都影响全网;system 变成垃圾桶,放大爆炸半径。

### 缺陷 6:异常与错误码(散落 + 冲突 + 侵入)

- **证据**:18 个 `ErrorCodeConstants` 常量接口;段位手工分配,实测 yudao **17 个跨模块冲突码**(im 与 mes 在 `1_040_*` 段重叠)。`throw exception(` 出现 yudao **1980 次** / xiaoqu **1924 次**,错误处理深度侵入业务。
- **根因**:常量接口 + 手工段位 + 静态 throw,无启动期注册校验。

### 缺陷 7:异常处理粗(HTTP 语义全 200)

- **证据**:yudao `GlobalExceptionHandler` 所有 `@ExceptionHandler` 返回 `CommonResult` POJO。`AccessDenied`→body `code:403` 但 **HTTP status 仍 200**;全仓 `@ResponseStatus` 命中 0。
- **影响**:权限不足应 403、参数校验应 400、未登录应 401,实际全 200;网关/监控/重试/限流/RESTful 集成全部失效。
- **⚠️ 客观提醒**:这是 yudao **与 Snowy 共有**的国内脚手架普遍范式,非 yudao 独有。Aurora 定位为「相对 yudao/Snowy 的差异化改进」。

---

## 2. Aurora 新范式:逐条改进

### 改进 1:消灭循环依赖 —— 单向依赖 + 事件 + 契约

**原则**:**模块内 Service 之间禁止直接持有对方的引用做"联动"**;跨域联动走契约接口或领域事件。

| yudao 坏味道 | Aurora 新范式 |
|---|---|
| `ProductSpuServiceImpl @Lazy ProductSkuService` 互注 | SPU/SKP 分属同一商品域,合并为一个 `ProductService` 或拆为独立子域后走 `ProductSkuApi` 单向 |
| Service 直接 `@Resource` 别的 Service | 跨域必须走 `XxxApi` 接口(单体本地 Impl / 微服务 Feign) |
| 用 `@Lazy` 兜底 | **禁用**。`@Lazy` 只允许极少数有充分理由的场景,且必须注释说明 |
| 全局 `allow-circular-references: true` | **关闭**。出现循环依赖 = 设计错误,编译/启动即失败,逼开发者拆 |

**配套**:引入领域事件(Spring `ApplicationEvent` / `ApplicationEventPublisher`)处理"完成 A 后触发 B"这类反向通知,从根上消除二元环。

### 改进 2:数据权限升级 —— 表维度 + API 维度双模

**融合 yudao(表维度)与 Snowy(API 维度)**,两种粒度共存:

- **表维度**(沿用 yudao `@DataPermission`,简单场景):注册表 → 自动拼 WHERE。
- **API 维度**(吸收 Snowy S1,精细场景):`DataScope{apiUrl, dataScope, scopeAll}` 挂登录用户,运行时按当前 servletPath 取范围;预计算表 `*_DATA_SCOPE(userId, scopeKey, orgId)` + `*_DATA_SCOPE_MAP(userId, apiUrl, scopeKey)`,scopeKey=MD5(orgId 集合)去重,inSql 子查询下推(SQL 长度固定可缓存)。
- **剥离 Sa-Token**:`DataScope` 挂 Aurora 的 `LoginUser`(Spring Security 上下文),非 Snowy 的 `StpUtil`。

### 改进 3:认证改用标准 Spring Authorization Server

**弃用 yudao 自造 token,改用 SAS**(吸收 dante-cloud 的 ConfigurerManager 封装思路):

- 标准 endpoint:`/oauth2/token`、`/oauth2/authorize`、`/oauth2/jwks`、`/oauth2/userinfo`、`/oauth2/revoke`、`/oauth2/introspect`
- JWT/JWK(默认) 或 opaque token(可选,强撤销场景)
- 标准 grant types:authorization_code、client_credentials、refresh_token、password(兼容期)、device_code
- PKCE、OIDC discovery、token revocation 开箱即得
- passkey/WebAuthn、社交登录(OAuth2/justauth)作为扩展
- ConfigurerManager 模式:把 SAS 的配置器封装成可注入 Bean,业务方零配置(dante-engine 的设计,需基于 Boot4 SAS API 自研,见 design §6.9)

> 这是与 yudao 范式**最大的不兼容点**。迁移时 token 体系整体重建,旧 token 表废弃。

### 改进 4:重设命名与分层 —— 收敛维度,统一模型

**四个正交维度收敛**:

| 维度 | yudao(全铺成包) | Aurora(收敛) |
|---|---|---|
| 分层 | controller/service/dal/api | 保留(controller/service/repository/integration) |
| 端(admin/app) | controller/admin + controller/app(但 dal 不切) | **按端分 controller 子包,dal 不分端**(共享 DO) |
| 领域 | 包目录 + 类后缀双标 | **只在包目录体现**,类名不加领域后缀 |
| 用途 | vo/dto 子包 + ReqVO/RespVO 双标 | **DTO 模型统一**(下文) |

**VO 收敛**(从 7 种 → 2~3 种):
- `XxxCreateReq` / `XxxUpdateReq`(或合并为 `XxxSaveReq`)— 写操作入参
- `XxxResp` — 读操作返回
- 分页:`XxxPageReq extends PageReq`(query 条件 + 分页参数)→ `PageResult<XxxResp>`
- **取消** SimpleRespVO / ExportReqVO / ExcelVO(用 `XxxResp` + 字段过滤 / 导出注解替代)

**实体模型统一**(从三份 → 一份):
- 一份 `XxxEntity`(持久层,`@TableName`)
- 跨模块契约用 DTO(精简字段),但 **DTO 由 MapStruct 从 Entity 生成**,不手写三份重复字段
- `dal/dataobject` → 重命名为 `dal/entity` 或 `repository/entity`(dataobject 是 yudao 生造词,不符通用习惯)

**包路径收敛**(从 6 层 → 4 层):
```
cn.aurora.module.<module>.<layer>.<feature>
例:cn.aurora.module.product.service.spu.SpuService
   (而非 yudao 的 ...controller.admin.product.vo.spu.SpuPageReqVO 6 层)
```

### 改进 5:模块边界 —— kernel vs business 分离

**拆分 yudao 的"上帝模块 system"**:

```
aurora-module-kernel        # 纯技术内核:auth/oauth2/permission/dict/tenant/user(role/dept/post)
                            # 只含"平台基础设施",不含任何业务
aurora-module-organization  # 销售组织、项目、部门业务(xiaoqu 错放在 system 的 organization/project)
aurora-module-payment       # 支付桥接(xiaoqu 错放在 system)
aurora-module-...           # 业务域各自独立模块
```

**原则**:
- kernel 只放"任何业务都要用的纯技术能力"(认证、授权、字典、租户、用户基础)
- 业务功能禁止落 kernel;找不到家的业务要么新建模块,要么并入最相关的业务模块
- kernel 扇入高是合理的(它是地基),但**业务模块扇入要尽量低**

### 改进 6:错误码 —— 注册表 + 自动校验 + 枚举

**重设**:
- 错误码用**枚举**(每个模块一个 `XxxErrorEnum implements ErrorCode`),而非散落的 `ErrorCodeConstants` 常量接口
- 启动期**注册表校验**:所有错误码注册到一个 `ErrorCodeRegistry`,`@PostConstruct` 检测数字 code 重复,重复则**启动失败**(消灭 yudao 的"运行时才发现冲突")
- 段位由模块名 hash 自动分配,或集中配置,杜绝手工撞段
- `throw` 侵入问题:保留 `throw new ServiceException(errorCode)`(异常流是 Java 惯用法),但错误码定义集中化、校验自动化

### 改进 7:HTTP 语义归位 —— 状态码与 body 分工

**改进**(相对 yudao/Snowy 的差异化):
- **HTTP status 反映语义**:权限不足 403、参数校验 400、未登录 401、未找到 404、业务错误 422(或自定义 4xx)、成功 200
- **body 仍带 `CommonResult`**(兼容前端):`ResponseEntity<CommonResult>` 双重表达
- 用 `@ResponseStatus` 或 `ResponseEntity` 控制 status,而非 yudao 的"全 200 + body code 模拟"
- 监控/网关/重试/限流按真实 HTTP status 工作,符合 RESTful

---

## 3. 新范式与四家的取舍关系

| 维度 | Aurora 新范式 | 来源 |
|---|---|---|
| 循环依赖 | 禁止 Service 互注 + 事件解耦 + 关 allow-circular-references | 新设计(纠正 yudao) |
| 数据权限 | 表维度 + API 维度双模 + 预计算表 + scopeKey | yudao(表)+ Snowy(API 维度) |
| 认证 | Spring Authorization Server + ConfigurerManager | dante-cloud(改掉 yudao 自造 token) |
| 命名分层 | 4 维收敛 + VO 2~3 种 + 实体模型统一 + 包 4 层 | 新设计(纠正 yudao) |
| 模块边界 | kernel vs business 分离 | 新设计(纠正 yudao 上帝模块) |
| 错误码 | 枚举 + 注册表自动校验 + 启动期查重 | 新设计(纠正 yudao) |
| 异常处理 | HTTP status 真语义 + body 兜底 | 新设计(超越 yudao/Snowy) |
| 跨模块契约 | 单向 api 接口(本地/Feign)+ 领域事件 | yudao 双轨 + dante 一套代码两架构 |
| 一套代码两架构 | @ConditionalOnArchitecture 配置驱动 | dante-cloud/engine |
| 统一响应 | CommonResult(无 success)+ traceId | yudao + Snowy |
| 字段加密 | SM4/AES + TypeHandler 透明加密 | Snowy |

**结论**:Aurora 不再是"yudao 改名 + 升 Boot4",而是一个**主动纠正 yudao 7 缺陷、融合四家所长的全新范式**。xiaoqu 迁移 = 重写。

---

## 4. 对迁移的影响

迁移策略从「改包名搬家」变为「**全部重写,不妥协**」:

- 不是 `cn.xiaoqu → cn.aurora` 全局替换,而是**按新范式逐模块重写**(kernel → 各业务域)
- 旧代码作为"需求参照"(业务逻辑照搬,但分层/命名/契约/数据权限按新范式重做)
- 迁移顺序仍按依赖图(详见 `migration/aurora-migration-plan.md`,将重写),但每个模块的迁移工作量 = 重写,不是改包名
- 这意味着周期更长,但产物是一个没有 yudao 历史债的干净平台

---

## 附录:核实证据来源

- yudao:`/Users/xq/01-code/xq/ruoyi-vue-pro` 的 `origin/master-jdk25`(`git show` 读取)
- xiaoqu:`/Users/xq/01-code/xq/xiaoqu-platform`(当前工作区)
- Snowy:`/Users/xq/01-code/xq/Snowy`(v3.6.5)
- dante-cloud/engine:`/Users/xq/01-code/xq/dante-cloud` + `/Users/xq/01-code/xq/dante-engine`(v4.1.0.4)
- 关键统计:@Lazy yudao 297/xiaoqu 110;VO 后缀 yudao ReqVO 823/RespVO 588 等;错误码 17 处跨模块冲突;throw exception yudao 1980/xiaoqu 1924;@ResponseStatus 命中 0
