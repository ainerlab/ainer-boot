# Aurora 迁移路线 · xiaoqu-platform → aurora-boot

> 状态:**DRAFT v0.2** · 日期:2026-07-22
> 目标:将 xiaoqu-platform 现有 13+ 业务模块(~6000 文件 / ~57 万行)迁入 aurora-boot
> 前提变更:~~Aurora 与 xiaoqu yudao 范式 100% 兼容~~ → **新范式不预设继承 yudao,主动改 7 缺陷**。迁移**不是改包名搬家,而是按新范式重写**。详见 `design/paradigm-redesign.md`

---

## 0. 迁移本质:**重写,不是搬家**

> ⚠️ 本节是 v0.1 → v0.2 的根本变更。旧版"改包名 + 升 Boot4 + 业务逻辑零改动"已作废。

**每个模块的迁移 = 按新范式重写**。xiaoqu 旧代码作为**需求参照**(业务规则照搬),但工程结构按新范式重建:

| 维度 | 旧代码(xiaoqu/yudao) | 迁移后(Aurora 新范式) | 性质 |
|---|---|---|---|
| 包名 | `cn.xiaoqu.module.X` | `cn.aurora.module.X` | 重命名 |
| 分层 | controller/service/dal/dataobject/dal/mysql/api/dto 四套 | controller/service/repository/integration 收敛 | **重设**(缺陷 4) |
| VO | ReqVO/RespVO/PageReqVO/SaveReqVO/SimpleRespVO 7 种 | Create/Update/Save Req + Resp + PageReq 2~3 种 | **收敛**(缺陷 4) |
| 实体 | DO/Mapper/DTO 三份重复字段类 | Entity 一份 + MapStruct 生成 DTO | **统一**(缺陷 4) |
| 跨模块 | Service 互注 + @Lazy 兜底(110 处) | 单向 api 接口 + 领域事件,禁止 Service 互注 | **重设**(缺陷 1) |
| 认证 | 自造 OAuth2 token 表 | Spring Authorization Server 标准 | **重建**(缺陷 3) |
| 数据权限 | 表维度 SQL 重写 | 表维度 + API 维度双模 | **增强**(缺陷 2) |
| 错误码 | 常量接口散落 + 手工段位(冲突) | 枚举 + 注册表启动校验 | **重设**(缺陷 6) |
| 异常 | HTTP 全 200 + body code | HTTP status 真语义 + body 兜底 | **改进**(缺陷 7) |
| 模块边界 | module-system 上帝模块 | kernel vs business 分离 | **重拆**(缺陷 5) |
| 循环依赖 | `allow-circular-references: true` 兜底 | **关闭**,循环即设计错误 | **纠正**(缺陷 1) |

**业务逻辑(字段含义、校验规则、计算公式)照搬;工程范式(分层/命名/契约/依赖方向)按新范式重建。**

---

## 1. 现有模块全景(真实数据)

### 1.1 规模与定制程度

| 模块 | 文件数 | 代码行 | 定制程度 | 特殊技术 |
|---|---|---|---|---|
| xq-module-system | 547 | 46,322 | 中改(含 organization/project/payment 扩展域) | wx-java-mp/miniapp |
| xq-module-infra | 299 | 23,278 | 小改(文件存储国产化+codegen) | easyexcel/velocity/s3/cos |
| xq-module-member | 212 | 12,348 | 小改 | — |
| xq-module-cdp | 124 | 10,375 | **全新自研**(yudao 无 CDP) | 自研限流 |
| xq-module-bpm | 239 | 20,540 | 原样~小改 | **Flowable 6** |
| xq-module-wecom | 145 | 10,209 | **全新自研**(yudao 是 mp 不是 wecom) | **wx-java-cp** |
| xq-module-pay | 244 | 20,522 | 小改 | weixin-java-pay |
| xq-module-mall/product | 1,119 | 122,778 | 大改(31 功能域,23 个新增) | — |
| xq-module-mall/promotion | 629 | 52,029 | 大改(含 AI 客服 kefu) | websocket |
| xq-module-mall/trade | 1,161 | 130,810 | **重度定制(最重)** | — |
| xq-module-mall/statistics | 71 | 3,216 | 小改 | — |
| xq-module-mall/trade-api | 36 | 1,712 | 全新契约(打断循环) | — |
| xq-module-crm | 255 | 18,967 | 小改~中改 | @CrmPermission |
| xq-module-traffic | 29 | 1,273 | **全新自研**(迷你) | — |
| xq-module-erp-api | 8 | 354 | 全新契约 | — |
| xq-module-erp | 257 | 22,101 | 中改~大改(含 app/assistant) | — |
| xq-module-ai-api | 14 | 461 | 全新契约 | — |
| xq-module-ai | 421 | 48,333 | **重度定制**(27 功能域,yudao 仅 7) | **Spring AI 1.1.2 全家桶** |
| **合计** | **~6000** | **~570,000** | | |

### 1.2 废弃/可选(server pom 已注释禁用)

- `xq-module-report`(数据报表 Jasper)
- `xq-module-mp`(微信公众号,被自研 wecom 替代)
- `xq-module-iot-biz`(物联网)

迁移可不带这三个。

---

## 2. 依赖图(迁移顺序的关键依据)

### 2.1 依赖关系(箭头 = "依赖于")

```
                    infra ◄── (几乎所有模块)
                      ▲
                      │
                    system ◄── (member, cdp, bpm, wecom, pay, crm, traffic, erp, ai, product, promotion, trade)
                      ▲                                  
                      │                                  
   ┌──────────────────┼───────────────────────────┐
   │                  │                           │
contract 层:    ai-api ◄─ (ai, cdp, product, trade, promotion)
(只被依赖)      erp-api ◄─ (erp, product, trade)
                trade-api ◄─ (trade, member, product, promotion, statistics, erp)  [扇入最高契约]

业务层(按扇出排序):
  trade     → system(124), product(99), member(80), promotion(74), ai(60), pay(37), cdp(7), erp(4)   [扇出最大,依赖 10 模块]
  promotion → product(94), ai(27), member(23), system(10), trade(6)
  product   → ai(61)⚠️, system(59), infra(21), erp(10), member(7)   [对 ai 依赖 > system!]
  crm       → system(92), bpm(9), infra(4)
  bpm       → system(84)
  member    → system(67), trade-api(10)
  erp       → system(43), trade(15), product(4)
  ai        → wecom(13)⚠️, infra(13), system(9), member(6), cdp(2)   [ai→wecom→ai-api↔cdp 三方圈]
  cdp       → system(4), crm(4), ai-api(4), member(1)
  statistics→ trade-api(10), pay(3), product(2)
```

### 2.2 关键耦合发现(迁移重点)

1. **system 是扇入王**(被所有模块依赖)→ **必须最先迁**,没它一切断裂
2. **infra 是第二地基**(文件/配置/codegen,被几乎所有模块依赖)
3. **trade-api 是解耦关键**(打断 trade↔promotion 循环,被 6 模块依赖)→ 迁移时**必须保留契约层**
4. **ai ↔ cdp 互依赖**(ai→cdp pom 依赖 + cdp→ai-api + ai 反向 import cdp event)→ **必须一起迁**
5. **ai → wecom**(ai 依赖企微客服 13 处)→ **wecom 先于或同步于 ai**
6. **product 对 ai 依赖(61)超过 system(59)** → product 的 AI 商品分析深度耦合 ai,**不可单独迁**
7. **trade 扇出 10 模块**、13 万行、已变质为"交易+导购CRM+财务分账"复合体 → **最后迁,或评估拆分**

---

## 3. 分层迁移路线(L0 → L6)

每层**独立可验收**,失败不影响前一层。

### L0:地基契约(必须最先)

| 模块 | 文件 | 动作 | 风险 |
|---|---|---|---|
| `xq-common/biz/` 的 7 个 CommonApi | ~30 | 提取到 `aurora-common` 的 `biz/` 包,改包名 | **低**(纯接口/DTO) |

**为什么第 0 步**:这 7 个 CommonApi 是 system/infra 被"反向依赖"时的解耦阀门。业务模块依赖 `aurora-common` 接口,system/infra 提供实现。**不先迁它,所有业务模块的日志/租户/权限/token/字典调用都会断。**

CommonApi 清单:`OAuth2TokenCommonApi` / `PermissionCommonApi` / `TenantCommonApi` / `DictDataCommonApi` / `OperateLogCommonApi` / `ApiAccessLogCommonApi` / `ApiErrorLogCommonApi`。

### L1:双地基(基础设施 + 权限底座)

| 模块 | 文件 | 定制 | 动作 | 风险 |
|---|---|---|---|---|
| `xq-module-infra` | 299 | 小改 | 改包名迁入 `aurora-module-infra`,保留 S3/COS 国产化存储 | 中(文件存储多后端) |
| `xq-module-system` | 547 | 中改 | 改包名迁入 `aurora-module-system`,保留 organization/project/payment 扩展域 | 中(扇入最高) |

**验收**:infra + system 在 aurora-boot 编译通过,system 的 16 个 Api 接口可用,infra 文件上传/代码生成可用。

### L2:独立低耦合模块

| 模块 | 文件 | 定制 | 动作 | 风险 |
|---|---|---|---|---|
| `xq-module-traffic` | 29 | 全新自研 | 改包名迁入(零耦合,随时可迁) | 低 |
| `xq-module-pay` | 244 | 小改 | 改包名迁入,保留 weixin-java-pay | 低 |
| `xq-module-member` | 212 | 小改 | 改包名迁入,**注意反向依赖 trade-api** | 低 |
| `xq-module-bpm` | 239 | 原样~小改 | 改包名迁入,**验 Flowable 版本与 Boot4 兼容** | 中(Flowable) |

**风险点**:`xq-module-bpm` 用 Flowable **6**(71 文件引用 org.flowable),Boot4 + Flowable 兼容性需验证。ruoyi master-jdk25 用 Flowable **8.0.0**,可能需同步升级。

### L3:契约先行 + 企微/CRM

| 模块 | 文件 | 定制 | 动作 | 风险 |
|---|---|---|---|---|
| `xq-module-ai-api` | 14 | 全新契约 | **先迁**(纯 DTO/接口,无逻辑) | 低 |
| `xq-module-erp-api` | 8 | 全新契约 | **先迁** | 低 |
| `xq-module-trade-api` | 36 | 全新契约 | **先迁**(打断循环,6 模块依赖) | 低 |
| `xq-module-wecom` | 145 | 全新自研 | 改包名迁入,保留 wx-java-cp;**是 ai 前置** | 中(自研无 yudao 参考) |
| `xq-module-crm` | 239 | 小改~中改 | 改包名迁入,保留 @CrmPermission 注解;**被 cdp 反向依赖** | 中 |

### L4:AI + CDP 复合体(一起迁)

| 模块 | 文件 | 定制 | 动作 | 风险 |
|---|---|---|---|---|
| `xq-module-ai` | 421 | **重度定制** | 改包名迁入,Spring AI 全家桶 + MCP + 4 向量库 + 5 自研模型适配 | **高** |
| `xq-module-cdp` | 124 | 全新自研 | 改包名迁入;**与 ai 事件级互依赖,缺一不可编译** | 高 |

**为什么一起迁**:ai↔cdp 通过 Spring 事件(`AiConversationSummaryEvent`/`DTO`)互依赖,且 ai pom 直接 depend cdp。拆开迁会导致编译断裂。

**最大风险**:**Spring AI 1.1.2 与 Boot4 兼容性**。建议迁移前先做 PoC(单独起一个最小 Boot4 + Spring AI 项目,验证 chat/embedding/vectorstore/mcp 可用)。

### L5:mall 复合体(product → promotion → trade)

| 模块 | 文件 | 定制 | 动作 | 风险 |
|---|---|---|---|---|
| `xq-module-mall/product` | 1,119 | 大改 | 改包名迁入;**对 ai 依赖(61)>system(59),必须在 ai 之后** | 高 |
| `xq-module-mall/promotion` | 629 | 大改 | 改包名迁入;含 AI 客服 kefu;**保留 trade-api 打断循环** | 高 |
| `xq-module-mall/trade` | 1,161 | **重度定制(最重)** | 改包名迁入;**最后迁(扇出 10 模块)** | **极高** |

**trade 模块特殊评估**:13 万行,内部 advisor(导购CRM)/finance(财务分账)/attribution(归因)/champion(销冠)/persona(画像)/twin(数字孪生)/xiaoda(AI助手)等 22 个新增功能域,已不是"交易模块",而是复合产品。

**建议**:迁移前评估 trade 是否应**拆成独立模块**(如 `aurora-module-advisor` / `aurora-module-finance` / `aurora-module-trade-core`),而非整体迁。这会显著降低单模块复杂度,但需梳理 advisor/finance 对 trade-core 的内部依赖。

### L6:收尾

| 模块 | 文件 | 定制 | 动作 | 风险 |
|---|---|---|---|---|
| `xq-module-mall/statistics` | 71 | 小改 | 改包名迁入(依赖全是上游) | 低 |
| `xq-module-erp` | 257 | 中改~大改 | 改包名迁入(含 app/assistant 定制) | 中 |

---

## 4. 迁移通用动作清单(每模块执行 · 重写式)

> 与 v0.1 的"改包名清单"不同,这是"按新范式重写"清单。

```
【需求抽取】(从旧代码读,不搬代码)
□ 1. 通读旧模块,梳理:实体字段、业务规则、校验逻辑、接口清单、对外契约
□ 2. 记录为新模块的"需求规格"(文档/注释),作为重写依据

【按新范式重建】
□ 3. 新建 aurora-module-<X>,按新分层(controller/service/repository/integration)搭骨架
□ 4. 实体:一份 Entity(原 DO),@TableName;DTO 用 MapStruct 生成(非手写三份)
□ 5. VO 收敛:Create/Update/Save Req + Resp + PageReq(≤3 种),非 7 种后缀
□ 6. 跨模块:走单向 api 接口(本地 Impl/未来 Feign)+ 领域事件,**禁止 Service 互注**(缺陷 1)
□ 7. 数据权限:按需用 @DataPermission(表维度)或 API 维度 DataScope(缺陷 2)
□ 8. 认证:用 Spring Authorization Server 体系(缺陷 3),非自造 token
□ 9. 错误码:枚举 + 注册表,启动校验无冲突(缺陷 6)
□ 10. 异常:HTTP status 真语义 + body CommonResult(缺陷 7)
□ 11. 模块边界:业务不落 kernel;该独立就独立模块(缺陷 5)

【Boot4 适配 + 验证】
□ 12. jakarta.*(grep 排查 javax 残留,除 JDK 内置)
□ 13. Boot4 适配点(见 boot4-migration-notes.md):Redisson V4 / Security 7 lambda / boot4 starter 坐标 / easy-trans
□ 14. 编译:mvn clean compile -pl <module> -am(关 allow-circular-references,循环即报错)
□ 15. 测试:按需求规格写新单测(旧单测作参考)
□ 16. 集成:启动 aurora-server 验证接口
□ 17. 提交:git commit -m "rewrite(<module>): 按新范式重写迁入 aurora-boot"
```

---

## 5. 三大风险与缓解

### 风险 1:Spring AI 1.1.2 与 Boot4 兼容性(L4)

**影响**:ai 模块无法迁移,连带 cdp、product(promotion/trade 间接)受阻。
**缓解**:L4 启动前先做 **Spring AI PoC**——最小 Boot4 + Spring AI 项目,验证:
- chat/embedding/model 可用
- 4 个向量库(pgvector/milvus/qdrant/redis)至少 1 个可用
- MCP client/server 可用
- 5 个自研模型适配层(星火/问多多/midjourney/suno/siliconflow)的 HTTP client 在 Boot4 下正常

若 PoC 失败,需评估 Spring AI 升级版本或 Boot 版本回退策略。

### 风险 2:trade 模块 13 万行复合体(L5)

**影响**:单模块过大,迁移周期长,回归风险高。
**缓解**:L5 启动前评估**拆分方案**:
- `aurora-module-trade-core`(订单/售后/购物车/配送)
- `aurora-module-advisor`(导购 CRM:advisor/account/avatar/contact/channel)
- `aurora-module-finance`(财务分账:aggregator/allocation/settlement)
- 其余(attribution/champion/persona/twin/xiaoda)按需归并

拆分需先画 trade 内部子包依赖图,确定可拆边界。

### 风险 3:ai ↔ cdp ↔ wecom 三方互依赖圈(L3-L4)

**影响**:缺一不可编译,必须同步迁移。
**缓解**:L3 先迁 wecom + 3 个 api 契约,L4 同批迁 ai + cdp。迁移期间若 ai-api 契约有调整,三方需同步改。

---

## 6. 迁移里程碑(预估)

| 里程碑 | 内容 | 依赖 |
|---|---|---|
| M0 | aurora-boot 骨架可编译(阶段 2-3 完成) | — |
| M1 | L0+L1 完成:common 契约 + infra + system 就位 | M0 |
| M2 | L2 完成:traffic/pay/member/bpm 就位 | M1 |
| M3 | L3 完成:3 个 api 契约 + wecom + crm 就位 | M2 |
| M4 | Spring AI PoC 通过 | M0(可并行) |
| M5 | L4 完成:ai + cdp 就位 | M3 + M4 |
| M6 | L5 完成:product + promotion + trade 就位 | M5 |
| M7 | L6 完成:statistics + erp 就位,全量迁移完成 | M6 |

> 预估仅供参考,实际周期取决于 Spring AI PoC 结果、trade 拆分决策、回归测试覆盖度。
