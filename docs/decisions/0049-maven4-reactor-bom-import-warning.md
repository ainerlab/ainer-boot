# ADR-0049：Maven 4 同 reactor BOM import 告警

- 状态：Accepted
- 日期：2026-08-25
- 决策者：Ainer 项目维护者
- 取代：无（修订 [ADR-0026](0026-maven-4-build-and-consumer-pom-baseline.md) 中
  「同 reactor BOM 导入告警在后续模型重构中处理」的开放项；不改写 ADR-0026 其余结论）
- 被取代：无

## 背景

Maven 4.0.0-rc-6 对「在同一 reactor 内 import BOM」给出模型告警。Ainer 根工程
`dependencyManagement` 显式 import 同 reactor 的 parentless `ainer-dependencies`，
这是 ADR-0026 有意保留的过渡结构：BOM 不得继承根 POM，否则形成 model import cycle；
外部消费者按 parentless 自包含 BOM 消费。

2026-08-25 在仓库根执行 `./mvnw -e validate`，告警原文为：

```text
BOM imports from within reactor should be avoided @ dev.ainer:ainer-boot:${revision},
file:///Users/xq/01-code/self/ainer-boot/pom.xml, line 73, column 13
```

能力审计 [Finding B-3](../reviews/java25-maven4-springboot41-capability-audit.md)
已定性：这不是漏写一段 XML，而是 reactor 拓扑与 parentless BOM 合同的交叉点。

## 决策驱动因素

- 不得破坏 parentless、自包含 BOM 的对外消费合同（ADR-0026）；
- BOM 坐标不得改回继承根 POM；
- Maven 4 仍是 preview（rc-6），GA 可能改变诊断或提供一等 BOM 模型；
- 消除告警的改动必须同时通过 `./mvnw validate`、`scripts/verify-maven-consumers.sh`，
  必要时 `artifact:compare`。

## 备选方案

### 方案 A：保持现状并接受告警

根工程继续 import 同 reactor BOM；子模块继续靠父 POM 的 `dependencyManagement` 继承版本。
收益：消费合同与已发布 Consumer POM 不变。代价：Maven 4 持续报 1 条模型告警，并提示
future Maven 可能拒绝此类工程。

### 方案 B：根工程停止 import，各模块自行 import parentless BOM

把 import 从根 POM 下沉到每个子模块。收益：根工程不再是告警坐标。代价：子模块仍从
**同一 reactor** import `ainer-dependencies`，Maven 4 的诊断语义是「reactor 内 BOM
import」，告警大概率只是换位置或按模块倍增，并不能从模型上消除根因。全量子模块补
import 面积大，且必须重跑 Maven 3.9+/Maven 4 双消费者门禁。

### 方案 C：把 BOM 移出 reactor

独立构建/发布 `ainer-dependencies`，根工程再 import 已安装的外部 BOM。收益：从定义上
不再是 reactor 内 import。代价：改变发布拓扑、CI 顺序和本地 `validate` 前提（必须先
install BOM），属于另一条供应链决策，不能当作消警告的顺手清理。

## 决策

**采用方案 A：暂不消除该告警，等待 Maven 4 GA。**

本阶段明确：

1. 根工程继续 import 同 reactor 的 parentless `ainer-dependencies`；
2. BOM 保持 parentless、自包含，不得改为继承 `ainer-boot`；
3. 不把 import 下沉到各子模块来「赶走」根坐标上的告警——那不会改变 reactor 内 import
   这一根因，却会扩大 POM 面积并增加消费回归面；
4. 不把 BOM 移出 reactor，除非另立供应链 ADR 并完成双消费者与制品对比；
5. Maven 4 GA 或官方提供一等 `packaging=bom` / 非 import 对齐方式后，再用独立原型
   重评方案 B/C；原型不过则继续本结论。

方案 B 的桌面推演（不落地 POM）：子模块 import 仍指向 reactor 内
`dev.ainer:ainer-dependencies:${revision}`，无法满足「消除 reactor 内 BOM import」；
在未证明 `verify-maven-consumers.sh` 全绿前，禁止为消警告改消费合同。

## 后果

### 正面

- parentless BOM 与已验证的 Maven 3.9+/Maven 4 消费合同保持不变；
- 不把 preview 工具链的诊断升级误当成必须立刻改模型的缺陷；
- ADR-0026 的开放项有了可引用的结论，不再每次评审重新争论。

### 负面与风险

- `./mvnw validate` 将继续出现上述 1 条模型 WARNING；
- Maven 4 GA 若把该诊断升级为错误，必须在升级 Wrapper 的同一变更里重开原型。

## 安全、数据与隐私

无运行时、身份或数据影响。只约束生产者 POM 模型。

## 运维与迁移

无需 migration。文档与门禁继续把该 WARNING 视为已知、已决策的模型噪声，不得用
`-q` 掩盖其他告警。发布与消费者验证流程不变。

## 验收记录

- 2026-08-25：`./mvnw -e validate` 复现完整告警原文（根 `pom.xml` 第 73 行）。
- 方案 B/C 未落地；未改 BOM 坐标、未跑会改合同的 POM 重构。
- `scripts/verify-maven-consumers.sh` 与 `artifact:compare` 仅在未来真正改 POM
  模型时作为准入，不作为本 ADR 的已完成证据。

## 参考

- [ADR-0026](0026-maven-4-build-and-consumer-pom-baseline.md)
- [Finding B-3](../reviews/java25-maven4-springboot41-capability-audit.md)
