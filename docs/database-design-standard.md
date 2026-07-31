# Ainer 数据库设计规范（PostgreSQL 18）

> 文档类型：长期规范 · 状态：生效 · 规范版本：1.2 · 最近核对：2026-07-30 · 适用版本：`0.1.x`

## 1. 目的、范围与规则等级

本文定义 Ainer 自有数据模型在 PostgreSQL 18 上的统一设计语言，覆盖命名、数据类型、表结构、完整性、tenant 隔离、索引、安全、演进、测试和评审。它是新建或修改 Ainer 自有表时的权威规范；[`database.md`](database.md) 继续负责数据库归属、已有表、Flyway 执行和运行手册。

Ainer 是 greenfield 脚手架，不提供 MySQL、H2、旧 PostgreSQL、yudao 表结构或当前早期实现的
兼容模式。规范以 PostgreSQL 18 stable 的原生能力为最低基线，决策依据见
[ADR-0020](decisions/0020-postgresql-native-greenfield-baseline.md)。

规则等级：

- **必须**：违反即不得合并，除非已接受 ADR 明确记录原因、风险和退出方案；
- **应当**：默认选择，偏离时必须在设计或评审记录中说明理由；
- **可以**：按真实业务和负载选用，不能因“以后可能有用”提前引入；
- 文中的 DDL 是设计范例，不是可直接执行的 migration，名称和字段必须按所属领域调整。

适用边界：

- 新表、新列、新约束和新索引必须遵守本文；
- 已在共享环境执行的 migration 在正式 baseline reset 前不因本文静默改写；差异按第 14 节治理；
- Spring Authorization Server、Spring Security WebAuthn 等框架协议表优先服从上游官方 schema；
- 外部系统的 opaque ID 可以保留其字符串语义，不得伪造为 Ainer UUID；
- 跨数据库、跨服务的数据边界通过契约或可靠事件连接，不建立虚假的跨库外键。

## 2. 总体原则

### 2.1 先设计不变量，再设计表

创建表前必须回答：

1. 该数据由哪个 bounded context 所有，谁可以写；
2. 它是聚合、从属实体、关联、事实/审计、outbox、投影还是协议存储；
3. 业务主键、幂等键和去重边界分别是什么；
4. tenant、主体和资源的归属如何在数据库中被证明；
5. 状态有哪些，哪些转换合法，哪些数据不可变；
6. 主要查询、排序、分页、预期基数和保留期是什么；
7. 哪些字段属于秘密、凭证、个人信息或模型敏感数据；
8. 删除、归档、恢复、法律保留和灾难恢复要求是什么。

无法回答上述问题时，不得先生成 DDL 再反推领域模型。

这里的“所有者”只表达**单一写入责任**，不引入额外的数据库所有权系统：

- 每张 Ainer 自有表必须有且只有一个 owner bounded context；
- migration、Mapper、Repository 和修改该表的 application use case 必须位于 owner 模块；
- 非 owner 不得直写该表，也不得复用 owner 的 Row/DO/Mapper 作为共享模型；跨边界协作使用公开
  application port、契约或可靠事件；
- 只有真实跨边界查询已经出现且公开端口不能满足时，才设计有明确 owner、刷新方式和一致性语义
  的读取投影；
- owner 记录在新表设计说明和代码位置中即可。不得为此预建 PostgreSQL schema、数据库角色、
  ownership registry、`owner_module` 字段或强制表注释。

共享 PostgreSQL、同一进程或相似字段都不是共享写入权的依据。这里不限制 owner 模块内部如何按
聚合组织表，也不预先替外部业务系统登记模块名称。

### 2.2 数据库承担结构不变量

应用授权不能替代数据库完整性。以下约束应尽量在数据库表达：

- 主键、外键、唯一性和 tenant 同属关系；
- 必填、合法取值、数值范围和时间先后关系；
- 开放申请、默认成员、ACTIVE OWNER 等可由唯一或部分唯一索引表达的不变量；
- 乐观锁版本和可靠事件状态。

跨行复杂状态转换、外部授权、跨数据库一致性仍由应用用例控制，并通过事务和集成测试证明。不得把业务规则藏在数据库 trigger 中；如确有必要，必须先立 ADR。

### 2.3 围绕查询建模，不为假想扩展建模

- 表、索引和冗余字段必须能指向当前用例或已接受 ADR；
- 不创建“万能扩展表”、EAV、无边界 JSON、备用字段或预留列；
- 不因为 PostgreSQL 提供某项能力就默认启用；
- 性能判断必须基于代表性数据量、查询计划和写入成本。

### 2.4 PostgreSQL Native-First

“使用 PostgreSQL”不只是把通用 SQL 发给 PostgreSQL。新设计必须先评估 PostgreSQL 18 的原生
表达，再决定是否退回应用实现：

| 能力 | Ainer 规则 |
|---|---|
| `uuidv7()` / `uuid_extract_version()` | Ainer 新持久化身份的默认生成与版本约束 |
| range / multirange + `WITHOUT OVERLAPS` / `PERIOD` | 有效期、占用、订阅、排班等时间区间的首选不变量 |
| `VIRTUAL` / `STORED` generated column | 确定性同一行派生值按读写成本选择，必须显式写明种类 |
| `RETURNING OLD/NEW` | 原子取得变更前后值，避免额外查询；不替代安全审计 |
| `COPY ... ON_ERROR ... REJECT_LIMIT` | staging 批量导入的受控容错，不允许无限忽略错误 |
| 部分/覆盖/表达式、BRIN、GIN、GiST、skip scan | 按真实查询、基数和写放大选用 |
| named/`NOT VALID` constraints | 大表在线约束演进和后续验证 |
| AIO、checksums、VACUUM 与统计 | 属于运行基线，在运维和环境验收中落实 |

Native-First 不等于功能堆砌。每项能力仍必须有业务不变量、查询需求或运行验证，但“兼容其他数据库”
不能成为拒绝 PostgreSQL 18 正确表达的理由。

## 3. 命名规范

### 3.1 通用规则

- 数据库标识全部使用小写 `snake_case` ASCII；
- 禁止带引号的大小写混合标识、拼音缩写和无业务含义缩写；
- 名称必须表达领域语义，禁止 `t_`、`tb_`、`sys_`、`biz_`、`data`、`info`、`detail`、`list` 等空泛命名；
- PostgreSQL 标识上限为 63 bytes。所有表、列、约束和索引名称必须在创建前检查长度；
- 表和领域名使用单数，例如 `ainer_identity_user`，不使用复数表名；
- SQL 关键字不得作为表名或列名。

### 3.2 表名

Ainer 自有表使用：

```text
ainer_<module>_<aggregate_or_fact>
```

示例：

```text
ainer_identity_user
ainer_workspace_member
ainer_ai_invocation
ainer_identity_access_event
```

规则：

- `<module>` 必须与代码模块或 bounded context 一致；
- 关联表用两端稳定领域名，如 `ainer_workspace_member`；
- 审计、事件、归档、申请必须在名称中显式体现，如 `_audit`、`_event`、`_archive`、`_request`；
- 同一数据库当前使用默认 schema。不得用多个 PostgreSQL schema 模拟服务边界；引入多 schema 必须有 ADR 和权限、Flyway、备份方案；
- 框架协议表保留官方名称，如 `oauth2_authorization`、`user_credentials`。

### 3.3 字段名

优先使用完整领域词汇和稳定后缀：

| 后缀 | 语义 | 示例 |
|---|---|---|
| `_id` | 主体、资源或关联标识 | `tenant_id` |
| `_code` | 稳定、可校验的业务编码 | `reason_code` |
| `_name` | 用户可见名称 | `display_name` |
| `_type` | 分类 | `actor_type` |
| `_status` | 生命周期状态 | `publication_status` |
| `_at` | 时间线上的瞬时点 | `occurred_at` |
| `_date` | 不带时区的日历日期 | `billing_date` |
| `_count` | 计数 | `attempt_count` |
| `_amount` / `_cost` | 精确金额 | `actual_cost` |
| `_rate` | 比率 | `tax_rate` |
| `_hash` / `_fingerprint` | 不可逆摘要 | `password_hash` |
| `_version` / `version` | 协议或乐观锁版本 | `payload_version` |
| `_payload` | 有版本的开放载荷 | `event_payload` |

布尔字段必须读成无歧义的业务谓词，例如 `is_default`、`owner_user_exists`、`blocked`。禁止用 `0/1` 整数代替布尔值，也禁止 `not_disabled` 这类双重否定。一个 bounded context 内相同概念必须使用同一名称。

### 3.4 约束与索引名

新对象统一采用：

| 对象 | 格式 |
|---|---|
| 主键 | `pk_<table>` |
| 外键 | `fk_<child>_<relation>` |
| 唯一约束 | `uk_<table>_<semantics>` |
| CHECK | `ck_<table>_<rule>` |
| 普通索引 | `idx_<table>_<query_shape>` |
| 独立/部分唯一索引 | `ux_<table>_<rule>` |

名称表达业务规则或查询形状，不机械罗列所有列。已有 migration 中的 `uq_`、内联主键等名称保持不变；新对象不得继续扩散多套风格。

## 4. 标识与关系

### 4.1 主键

- Ainer 自有聚合和需要独立生命周期的实体，主键必须使用 PostgreSQL 原生 `uuid`；
- 禁止以 `varchar(36)`、无符号字符串或可变业务编码保存 Ainer UUID；
- Ainer 新增聚合、实体、事件、审计、outbox 和操作 request 的持久化 ID 必须使用 UUIDv7；
- 新表默认使用 `DEFAULT uuidv7()`，并以
  `CHECK (uuid_extract_version(id) = 7)` 阻止错误生成器写入；
- Repository 通过 `INSERT ... RETURNING id` 取得数据库生成值；必须在持久化前取得 ID 的用例
  依赖统一、可注入 `Clock` 的 `AinerIdGenerator`，业务代码不得直接调用 `UUID.randomUUID()`；
- UUIDv7 的时间只表示生成时间，不能代替 `created_at` / `occurred_at` 或领域排序；
- UUIDv7 不是 secret；Token、nonce、恢复码和 PKCE 材料使用协议规定的高熵随机值；
- 只有数据库内部、不会暴露为领域/契约标识的高吞吐序号才可以使用 `GENERATED ... AS IDENTITY`；禁止新用 `serial` / `bigserial`；
- 可变的 code、username、邮箱、外部 request ID 不能作为 Ainer 聚合主键。

### 4.2 关联表

- 纯关联且其身份就是两端关系时，应当使用复合主键，如 `(tenant_id, user_id)`；
- 只有关联本身需要被引用、独立审计或拥有复杂生命周期时才增加 UUID；
- 关联表必须明确唯一性，不能依赖“应用通常不会重复插入”；
- tenant-owned 关联优先把 `tenant_id` 纳入主键或复合外键，使跨 tenant 关联在数据库层失败。

### 4.3 外部和协议标识

- OAuth client ID、provider request ID、JWT `sub` 等 opaque ID 使用有业务上限的 `varchar(n)`；
- 外部 ID 必须标出来源和规范化方式，不得假定可解析为 UUID；
- 同一边界上的 producer 与 consumer 必须约定长度；数据库长度不应小于契约允许值；
- 协议表的 ID 类型以官方 schema 为准，不为了 Ainer 风格强制转换。

## 5. 数据类型选择

### 5.1 类型决策表

| 业务语义 | PostgreSQL 类型 | 规则 |
|---|---|---|
| Ainer 实体标识 | `uuid` | 见第 4 节 |
| 布尔谓词 | `boolean` | `NOT NULL`，确有三态语义才允许 NULL |
| 有界计数 | `integer` | 必须有非负/范围 CHECK |
| 长期累计、Token 总量、版本 | `bigint` | 必须有非负 CHECK；乐观锁从 0 开始 |
| 精确金额/费率 | `numeric(p,s)` | 按最大值和最小计价单位推导，禁止浮点 |
| 短且有领域上限的文本 | `varchar(n)` | `n` 是业务/安全/互操作边界，不默认 255 |
| 无固定业务长度的正文 | `text` | API 和应用仍必须限制载荷大小 |
| 固定规范编码 | `char(n)` | 仅哈希十六进制、ISO 货币等真实固定长度值 |
| 现实世界瞬时点 | `timestamptz` | Java 使用 `Instant`，统一 UTC |
| 日历日期 | `date` | 不代表一个瞬时点 |
| 本地时间 | `time` | 仅纯营业时间等；必须另有时区规则 |
| 半结构化扩展载荷 | `jsonb` | 受第 5.6 节约束 |
| 二进制协议材料 | `bytea` | 不以 Base64 文本代替 |
| IP 地址/网段 | `inet` / `cidr` | 不用 `varchar` |

### 5.2 字符串

- `varchar(n)` 的长度必须来自领域规则、外部协议或安全上限，并在 API/应用层使用同一边界；
- `text` 不等于无限输入。请求大小、字段长度、日志和序列化必须另有限制；
- 需要保留展示值与进行匹配时，应保存原值与规范化值，或只保存规范化后的 canonical value；
- 大小写不敏感唯一性必须通过显式规范化列、表达式唯一索引或经过评估的非确定性 collation 实现，并在 Repository 查询中保持一致；
- `citext` 不是默认方案；启用 extension 会增加兼容与运维面，必须有明确理由；
- 不用空字符串表达 NULL。可选值缺失用 NULL，空字符串只有在领域中是一个合法且不同的值时才允许。

### 5.3 数字、金额和计量

- 金额、成本、余额、预算、税率和汇率禁止使用 `real`、`double precision` 或 PostgreSQL `money`；
- 精度必须按上界计算。普通业务金额建议从 `numeric(19,4)` 评估，AI 微计费可使用当前基线 `numeric(20,8)`；
- 金额必须同时有币种；币种使用大写 ISO 4217 `char(3)`，并在应用或参考数据中验证；
- 数量、金额和耗时必须带明确单位，禁止 `size`、`value`、`duration` 等无单位字段；
- 单次 AI 调用 Token 数在有严格上限时可用 `integer`，长期汇总必须用 `bigint`；
- 比率需用 CHECK 约束合法区间，例如 `rate BETWEEN 0 AND 1`；
- 近似浮点只允许科学测量、评分或向量计算等接受舍入误差的场景，并需说明容差。

### 5.4 时间

- `created_at`、`updated_at`、`occurred_at`、`expires_at` 等瞬时点必须使用 `timestamptz`；
- 禁止用 `timestamp without time zone` 保存现实世界的创建、支付、登录、审计或到期时间；
- 数据库存储瞬时点，展示时区在 API/UI 边界转换；不得保存“北京时间字符串”；
- 领域和审计时间默认由应用注入的 `Clock` 产生，便于事务语义与测试。`CURRENT_TIMESTAMP` 只用于数据库自身元数据且须明确说明；同一聚合不得混用两套时间来源；
- `updated_at >= created_at`、`expires_at > created_at` 等可验证关系必须使用 CHECK；
- 持续时长优先使用带单位的 `integer`/`bigint`，如 `latency_ms`、`ttl_seconds`；仅当数据库需要进行区间运算时使用 `interval`；
- 稳定分页必须在时间列后增加唯一 tie-breaker，例如 `(occurred_at DESC, id DESC)`。

### 5.5 状态与分类

- 应用拥有且会演进的状态默认使用 `varchar(n)` + 命名 CHECK；
- PostgreSQL ENUM 不是默认方案。删除、重命名、跨版本兼容困难；采用前必须有 ADR；
- 状态值使用大写 `UPPER_SNAKE_CASE`，代码枚举、API 和数据库必须一一对应；
- CHECK 只保证取值集合，合法状态转换由应用用例、条件更新/锁和测试保证；
- 类型与状态不得混用：`type` 回答“它是什么”，`status` 回答“它现在处于何种生命周期”；
- 新状态采用 expand-contract：数据库先接受，兼容代码再写入，旧消费者退出后再收紧。

### 5.6 JSON、数组和扩展

`jsonb` 只适合真正开放、供应商相关或版本化的载荷。使用前必须同时定义：

1. payload 类型和 `payload_version`；
2. 最大字节数和拒绝策略；
3. 允许/禁止的敏感字段；
4. 读取者、保留期和兼容策略；
5. 是否查询内部字段；若查询，给出具体 SQL 和索引设计。

以下情况必须使用普通列或从属表，而不是 JSON：

- 需要外键、唯一、非空、范围约束的核心业务字段；
- 高频过滤、排序、连接、计费或授权字段；
- tenant、subject、状态、时间、金额和数据所有权；
- 需要局部更新并发控制的字段。

不得默认创建 GIN 索引。PostgreSQL 数组只用于稳定、原子的值集合；实体关系使用关联表。OAuth 等上游协议表按官方序列化格式保存，不作为新业务表范例。

#### Domain Snapshot 模式

非标业务观察、模型输入和外部事实在某个时间点的**不可变来源快照**可以使用版本化 `jsonb`，
但快照不是可变属性袋。采用时必须同时保存：

- `snapshot_type`、`schema_version`、`captured_at`；
- 可追溯的 `source_id` / `source_version`，或等价来源引用；
- `content_hash` 和受大小限制的 `snapshot_payload`；
- 数据分级、保留和删除规则。

快照写入前必须通过对应 schema 校验，修订时追加新版本，不原地改写历史来源快照。tenant、授权、
状态、关系、金额、计量单位以及参与核心过滤/约束的字段仍必须结构化；未经验证的快照内容不得
直接成为授权或最终价格事实。反复参与查询、约束或计算的属性必须提升为普通列或从属实体，不得
通过默认 GIN 索引把快照变成隐式主模型。

### 5.7 二进制、大对象与向量

- 公钥、attestation 等协议二进制使用 `bytea`，不得保存私钥、生物识别模板或明文凭证；
- 文件、图片、模型产物和大正文默认存对象存储，数据库只保存受权限控制的对象引用、校验摘要和元数据；
- `pgvector` 是扩展，不是 PostgreSQL 18 核心类型。引入向量存储必须先决定所有权、维度、embedding 模型/版本、tenant/ACL 过滤、重建方式和容量，并通过 ADR；
- RAG chunk 的来源、权限、content hash、embedding model/version 必须是结构化列；向量索引类型和参数必须基于召回率、延迟和写入测试选择；
- 高容量模型 trace、完整 prompt/output 和 observability 明细不得默认进入业务 OLTP 库。

## 6. 标准表结构

### 6.1 可变聚合

按领域需要选择字段，禁止无脑复制“万能基础表”：

```sql
CREATE TABLE ainer_example_resource (
    id UUID NOT NULL DEFAULT uuidv7(),
    tenant_id UUID NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_ainer_example_resource PRIMARY KEY (id),
    CONSTRAINT ck_ainer_example_resource_id_version
        CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT uk_ainer_example_resource_tenant_id
        UNIQUE (tenant_id, id),
    CONSTRAINT uk_ainer_example_resource_tenant_code
        UNIQUE (tenant_id, resource_code),
    CONSTRAINT ck_ainer_example_resource_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_ainer_example_resource_version
        CHECK (version >= 0),
    CONSTRAINT ck_ainer_example_resource_time
        CHECK (updated_at >= created_at)
);
```

- `tenant_id` 只在 tenant-owned 数据中出现；
- `version` 只在需要并发更新的聚合中出现；
- `created_by` / `updated_by` 只有业务确实需要追踪操作者时才出现，值必须是可信主体；
- 不要求每张表都有 `status`、`updated_at`、`remark` 或软删除列。

### 6.2 关联/从属表

```sql
CREATE TABLE ainer_example_resource_member (
    tenant_id UUID NOT NULL,
    resource_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_ainer_example_resource_member
        PRIMARY KEY (tenant_id, resource_id, subject_id),
    CONSTRAINT fk_ainer_example_resource_member_resource
        FOREIGN KEY (tenant_id, resource_id)
        REFERENCES ainer_example_resource (tenant_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_example_resource_member_role
        CHECK (role IN ('ADMIN', 'MEMBER'))
);
```

父表若被 `(tenant_id, id)` 引用，必须存在相应 UNIQUE。是否允许 `CASCADE` 取决于子记录是否完全从属于父记录以及删除是否需要保留审计。

### 6.3 事实与审计表

事实/审计表通常是 append-only：

- 包含事件/操作 ID、tenant、actor、target、action/phase、`occurred_at` 和关联 request/reference；
- 不设置 `updated_at` 或软删除；
- 不记录 secret、Token、完整请求/响应或模型敏感正文；
- 更正通过新的补偿事实表达，不覆盖历史；
- 保留、归档和外部不可变副本是独立策略。

### 6.4 Outbox

可靠 outbox 至少表达：

- 稳定 event ID、aggregate/tenant/subject；
- `event_type`、`payload_version`、`occurred_at`；
- `publication_status`、`available_at`；
- `lease_owner`、`lease_until`、`attempt_count`；
- 已脱敏的 `last_error_code`，不得保存原始供应商错误正文。

业务事实和 outbox 必须同事务写入；relay 在短事务领取后执行网络调用，再用 event ID + lease owner 确认。消费者按 event ID 幂等。

## 7. NULL、默认值与删除

- 字段默认 `NOT NULL`；只有“未知/未发生/不适用”是合法业务状态时才允许 NULL；
- NULL、空字符串、0 和空 JSON 不得混作“没有值”；
- 默认值必须是安全的领域默认，不得为了方便 migration 隐藏调用方遗漏；
- 新增非空列到已有大表按“可空或临时默认 → 分批回填 → 验证 → 加约束 → 移除临时默认”演进；
- Ainer 不使用全局 `deleted` / `is_deleted` 基础字段；
- 需要恢复、保留或法律审计时，使用明确状态及 `deleted_at`/`deleted_by` 等领域字段，并定义对唯一索引和查询的影响；
- 审计、账务、事件、安全事实通常不可物理删除；最终清除必须遵守保留策略；
- 无业务恢复价值的数据优先硬删除，并通过 FK 的 `RESTRICT` 或经证明安全的 `CASCADE` 控制。

## 8. 约束与 tenant 完整性

### 8.1 主外键

- 外键两侧类型必须完全一致；
- 新外键显式声明 `ON UPDATE` 和 `ON DELETE`；
- 默认使用 `RESTRICT`；`CASCADE` 只用于完全从属、无需独立审计且已测试的子记录；
- `SET NULL` 只有父记录消失后子记录仍有清晰语义时才允许；
- Ainer ID 不可变，因此业务代码不得更新主键；
- PostgreSQL 不会自动为外键引用侧创建索引。父表删除/更新或关联查询需要时必须单独设计；
- 跨数据库、外部投影和异步副本不建外键，由契约、幂等与对账保证。

### 8.2 tenant

- tenant-owned 表必须显式保存可信 `tenant_id`，Repository 方法和 SQL 必须显式绑定它；
- Ainer 自有 tenant 标识在数据库、Java 领域、JWT claim 投影和内部事件中必须统一使用 `uuid`；
- Ainer 签发的 JWT `tenant_id` 必须解析为 UUID，非法值失败关闭，不能以“claim 是字符串”为由
  在业务库使用 `varchar`；
- 外部身份源的 tenant 使用独立 `(issuer, external_tenant_id)` 映射；不得把外部 opaque ID 写入
  Ainer `tenant_id`；
- 子资源通过 `(tenant_id, parent_id)` 复合外键证明同属关系；
- 唯一性通常以 tenant 为边界，如 `UNIQUE (tenant_id, resource_code)`；
- 应用层成员关系、scope 和角色校验不能替代 tenant 数据约束；
- 当前不默认启用 PostgreSQL RLS。引入 RLS 必须有连接池/session context、后台任务、migration、故障关闭和测试方案的 ADR。

### 8.3 CHECK 与唯一性

CHECK 应用于：

- 状态/类型取值集合；
- 非负、百分比、长度和格式；
- 开始/完成/到期时间关系；
- 状态与可空字段之间的条件关系。

禁止在 CHECK 中调用依赖其他表或可变外部状态的函数。跨表规则使用 FK、UNIQUE、事务锁或应用用例。

唯一性必须明确 NULL 语义。PostgreSQL 默认允许多个 NULL；业务要求“NULL 也只能有一个”时，使用 `UNIQUE NULLS NOT DISTINCT`。一个目标只允许一个开放申请、一个默认成员等条件唯一性使用部分唯一索引。

延迟约束 `DEFERRABLE` 不是默认选择，只能用于确有单事务循环依赖或批量重排的场景，并需专项测试。

## 9. 索引与查询设计

### 9.1 基本规则

每个索引必须记录服务的查询、约束或排序，禁止“可能以后会查”：

- PK 和 UNIQUE 已创建索引，不重复创建；
- 多租户查询通常先放 `tenant_id`，再放等值过滤列，最后放范围/排序列；
- PostgreSQL 18 的 B-tree skip scan 可改善部分非前导列查询，但不能代替围绕主查询设计列顺序；
- 大结果集使用 keyset pagination，如 `(occurred_at, id) < (?, ?)`，禁止依赖高 OFFSET；
- `INCLUDE` 只放覆盖查询所需、变化频率低的小字段；
- 部分索引谓词必须与稳定查询谓词一致；
- 表达式索引要求所有查询使用相同表达式；
- GIN、GiST、BRIN 和向量索引必须有数据规模与查询计划结果；
- 高频写表要评估每个索引的写放大、存储、VACUUM 和更新成本。

### 9.2 查询计划门禁

对高风险或大数据量查询，评审至少包含：

```sql
EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)
SELECT ...;
```

必须使用脱敏且接近目标基数的数据，记录：

- 估算行数与实际行数偏差；
- 索引/顺序扫描选择；
- buffer hit/read、临时磁盘和排序；
- 锁范围、执行时间和写入 WAL；
- 冷热数据、tenant 倾斜和最坏分页位置。

不得根据小型 Testcontainers 空库计划推断生产容量。生产排查优先 `EXPLAIN`，执行 `ANALYZE` 前必须确认不会造成不可接受负载。

### 9.3 大表与分区

分区不是默认能力。只有当预计行数、表/索引大小、保留删除、VACUUM 或查询裁剪已形成可测问题时采用。引入前必须有 ADR，说明：

- 分区键与访问模式；
- 唯一约束必须包含分区键带来的模型影响；
- 默认分区、未来分区预建和过期分区处理；
- Flyway、备份恢复、监控和故障处置；
- 分区数上界及跨分区查询计划。

超大 append-only 时间事实可以评估时间分区或 BRIN，但不得只凭行数猜测。

## 10. 安全、隐私与 AI 数据

### 10.1 数据分级

每个新表设计必须标注：

- 公开/内部/机密/高度敏感等级；
- 是否含个人信息、凭证材料、支付/账务或模型敏感数据；
- 加密、脱敏、访问审计、保留和删除要求；
- 是否允许进入日志、测试夹具、备份和分析环境。

密码只保存强密码哈希；Token、API key、恢复码和激活 secret 不保存明文。需要可恢复秘密时使用 KMS/Vault 或带认证的 envelope encryption，并保存 `key_version`；哈希和加密不能混为一谈。

### 10.2 AI 业务

- AI 调用审计保存 provider、requested/resolved model、Token、精确成本、币种、耗时、状态、策略和不可逆 fingerprint；
- prompt、模型输出、工具 secret、完整供应商错误正文默认不入库、不入日志；
- 经批准保存内容时，必须单独定义用途、同意/授权、tenant ACL、脱敏、加密、保留和删除；
- provider payload 不得成为业务事实的唯一表示，计费、授权、状态和可观测字段必须结构化；
- embedding 必须绑定模型、版本和维度。换模型视为新版本和可重建流程，不能静默覆盖；
- RAG 文档和 chunk 必须继承来源资源的 tenant/ACL，检索前过滤不能只依赖模型相似度；
- 用量与费用汇总必须可从不可变调用事实重新计算，并区分供应商实际值与估算值。
- AI execution 只拥有运行、模型调用、检索轨迹和平台产物；订单、商品、客户、估价结论等业务事实
  仍由业务 bounded context 拥有，不能因为“由 AI 生成”就迁入 AI 表；
- Knowledge 只拥有摄取、修订、切分、embedding、索引代际和检索轨迹。来源业务事实及其授权策略
  仍由来源 bounded context 拥有，Knowledge 保存的是可重建投影而不是新的事实权威；
- `Run`、`Invocation`、`Artifact` 和 Knowledge 的候选模型分别见
  [`design/ai-runtime-data-model.md`](design/ai-runtime-data-model.md) 与
  [`design/knowledge-data-model.md`](design/knowledge-data-model.md)。两份文档处于提案状态，
  不能据此一次性创建全部物理表。

## 11. Migration 与在线演进

文件命名和不可变性以 [`database.md`](database.md) 为准，并补充：

- migration 必须确定性执行；不要用 `IF NOT EXISTS` 掩盖 schema drift；
- 一个 migration 只完成一个可审查目的，结构变更和大批量数据回填通常分开；
- 已发布 migration 永不修改，修复只新增版本；
- 破坏性变更使用 expand-contract，不在同一发布中直接重命名/删除仍被旧代码使用的列；
- 大表增加 CHECK/FK 可评估先 `NOT VALID`、再独立 `VALIDATE CONSTRAINT`，并验证锁行为；
- `CREATE INDEX CONCURRENTLY` 不能运行在普通事务 migration 中，必须设计独立、可观测、可重试的发布步骤；
- 禁止 migration 连接外部服务、读取环境业务数据或写入真实账号/tenant/secret；
- DDL 和依赖代码必须具备向前兼容窗口；应用回滚不等于 schema 回滚；
- 每次变更都要说明失败点、锁范围、升级耗时、备份/恢复和向前修复方案。

## 12. MyBatis、MyBatis-Plus 与 Java 映射

| PostgreSQL | Java |
|---|---|
| `uuid` | `java.util.UUID` |
| `timestamptz` | `java.time.Instant` |
| `date` | `java.time.LocalDate` |
| `numeric` | `java.math.BigDecimal` |
| `bigint` | `long` / `Long` |
| `integer` | `int` / `Integer` |
| `boolean` | `boolean` / `Boolean` |
| `jsonb` | 版本化领域对象或 `JsonNode` |
| `bytea` | `byte[]` |

规则：

- `uuid` 通过项目显式 TypeHandler 以 `Types.OTHER` 绑定；
- 金额计算和比较使用 `BigDecimal`，禁止转 `double`；
- 持久化 Row/Mapper/Repository adapter 位于模块 infrastructure，领域、应用和 API 不依赖
  MyBatis/MyBatis-Plus 类型；
- `BaseMapper`、Wrapper、Page 和 MyBatis-Plus 注解只用于简单、单表且权限条件明确的
  infrastructure 实现；Repository 端口继续返回 Ainer 应用或领域类型；
- CTE、锁、`RETURNING`、advisory lock、outbox、审计归档、稳定游标等复杂或安全敏感路径使用
  显式 Mapper 方法和 XML；
- 全局 `IdType.AUTO` 只用于让 PostgreSQL `DEFAULT uuidv7()` 生成并通过 JDBC generated keys
  回填 ID；多返回列或显式变更语义仍使用 `RETURNING`。不得使用 `ASSIGN_ID` /
  `ASSIGN_UUID`；必须预分配 ID 的用例遵守 ADR-0020；
- tenant interceptor 当前不启用；所有租户查询仍显式绑定 tenant，不能依赖 ORM 插件替代授权；
- 不默认使用逻辑删除或 MetaObject 自动填充表达状态、actor、tenant、时间和审计事实；
- 分页使用 PostgreSQL 方言，最大单页 100；若存在其他 inner interceptor，分页放在链尾；
- SQL 必须参数绑定，tenant、排序字段和用户输入不得字符串拼接；
- 动态排序只能从服务端白名单映射为固定 SQL 片段；
- Repository 签名必须暴露 tenant 与并发条件；
- 乐观更新使用 `WHERE id = ? AND version = ?` 并原子增加版本，0 行更新映射为明确冲突；
- 大批量操作要限制 batch size，不把无限集合展开为单条 `IN (...)`。

MyBatis-Plus 的版本、依赖和架构边界见
[ADR-0028](decisions/0028-mybatis-plus-infrastructure-baseline.md)。

## 13. 设计交付与评审清单

### 13.1 新表设计说明

提交 DDL 前必须提供：

```text
所有者/模块：
允许写入者：
表类型：聚合 | 从属 | 关联 | 事实/审计 | outbox | 投影 | 协议
用途与不变量：
tenant/subject 归属：
主键、业务键、幂等键：
状态与合法转换：
主要查询、排序、分页：
预期日增量、三年行数、单 tenant 倾斜：
保留、归档、删除：
数据分级与禁止落库内容：
外部契约和失败恢复：
```

### 13.2 合并门禁

- 名称、类型、NULL、默认值均有领域依据；
- 单一 owner、写入路径和跨边界契约明确，未引入无真实消费者的投影；
- PK、FK、UNIQUE、CHECK 和 delete action 完整；
- tenant 同属关系不能只靠调用者自觉；
- 索引逐一映射真实查询，未重复 PK/UNIQUE；
- 状态、并发、幂等、失败回滚和跨 tenant 负例有测试；
- Testcontainers 从空 PostgreSQL 18 执行全部 migration；
- 升级路径在接近真实 schema/基数的数据库验证；
- MyBatis 映射覆盖 UUID、时间、numeric 和 nullable 字段；
- 文档、数据字典、API/事件契约与代码同一变更；
- `git diff --check` 通过，migration 时间戳全仓唯一。

### 13.3 AI 生成数据库代码的额外规则

AI 在输出 migration 前必须先输出第 13.1 节的设计说明。AI 不得：

- 猜测字段长度、货币精度、状态集合或 delete action；
- 默认添加 `id bigint`、`varchar(255)`、`deleted`、`remark`、JSON 扩展字段；
- 修改既有 migration；
- 从 Controller DTO 直接推导持久化模型；
- 把安全正文、secret 或 provider 原始 payload 加入审计表；
- 只生成建表 SQL，不生成约束、索引依据、Repository 映射与 PostgreSQL 集成测试。

## 14. 当前实现差异与 1.0 前治理

本文从 1.1 起约束新变更。Ainer 不为早期实现保留兼容模式；以下差异是 1.0 前必须主动消除的
技术债：

| 现状 | 原因 | 后续策略 |
|---|---|---|
| Identity 的 `tenant_id` 是 `uuid`，Workspace/AI 为 `varchar(128)` | 早期把 JWT claim 的传输类型误当持久化类型 | 全链路统一 UUID；外部 tenant 另建 issuer-bound 映射 |
| 持久化业务代码直接调用 `UUID.randomUUID()` | 早期垂直切片优先验证链路 | 引入 UUIDv7 生成/返回策略，逐项区分持久化 ID 与临时随机值 |
| migration 含 `legacy-unassigned/*` 回填 | Workspace 在早期切片后补 tenant | 1.0 clean baseline 不保留该伪 tenant；重建开发/测试数据库 |
| 已有约束名前缀存在 `uq_`、`uk_`、`ux_` | 早期 migration 风格未统一 | 1.0 baseline 统一；在此之前新增对象采用第 3.4 节 |
| 部分协议表使用较宽 `varchar` 或序列化文本 | Spring 官方 JDBC schema 的互操作要求 | 协议表跟随上游；Ainer 自有表不得照搬 |
| 部分从属关系使用 `ON DELETE CASCADE` | 早期模型认定为完全从属 | 新使用必须逐项证明不会删除审计/安全事实 |

在 baseline reset 正式执行前，不得静默修改已被共享数据库执行的 migration；这条规则保护工程
可重复性，不是对旧项目的产品兼容承诺。

## 15. PostgreSQL 18 基线与 PostgreSQL 19 前向策略

本规范以 PostgreSQL 18 官方文档为技术依据：

- [UUID 类型与 UUIDv7](https://www.postgresql.org/docs/18/datatype-uuid.html)
- [PostgreSQL 18 Release Notes](https://www.postgresql.org/docs/18/release-18.html)
- [数据定义约束](https://www.postgresql.org/docs/18/ddl-constraints.html)
- [日期与时间类型](https://www.postgresql.org/docs/18/datatype-datetime.html)
- [数值类型](https://www.postgresql.org/docs/18/datatype-numeric.html)
- [多列索引与 skip scan](https://www.postgresql.org/docs/18/indexes-multicolumn.html)
- [唯一索引与 NULL 语义](https://www.postgresql.org/docs/18/indexes-unique.html)
- [CREATE INDEX](https://www.postgresql.org/docs/18/sql-createindex.html)
- [JSON 类型与函数](https://www.postgresql.org/docs/18/functions-json.html)
- [`citext` 扩展](https://www.postgresql.org/docs/18/citext.html)
- [Generated Columns](https://www.postgresql.org/docs/18/ddl-generated-columns.html)
- [DML `RETURNING`](https://www.postgresql.org/docs/18/dml-returning.html)
- [`COPY`](https://www.postgresql.org/docs/18/sql-copy.html)

规范中的业务选择仍以 Ainer 的边界、安全和运行验证结果为准；PostgreSQL 支持某种类型或索引不代表项目必须采用。

截至 2026-07-26，PostgreSQL 19 仍处于 Beta 2。Ainer 应建立非阻塞前向测试，关注 temporal
`FOR PORTION OF`、`REPACK CONCURRENTLY`、`WAIT FOR LSN`、logical replication sequence 和
锁/恢复统计；PG19 GA 且驱动、Testcontainers、备份恢复与真实 workload 验证完成前，不作为生产
基线。[PostgreSQL 19 Beta 2 公告](https://www.postgresql.org/about/news/postgresql-19-beta-2-released-3350/)
