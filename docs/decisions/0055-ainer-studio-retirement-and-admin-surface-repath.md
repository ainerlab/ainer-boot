# ADR-0055：Ainer Studio 产品线退役与管理面交付路线改道

- 状态：Accepted
- 日期：2026-09-05
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无
- 修订：ADR-0022 与 ADR-0054 中依赖 Ainer Studio 的交付假设；本 ADR 生效后，
  管理面参考实现不再由独立 Studio 仓承载，`Ainer Studio` 与 `@ainer` 不再是
  活动技术标识（ADR-0004 命名基线中相应条目失效）

## 背景

Ainer Studio 定位为 React + Ant Design 设计系统产品（Blocks、Templates、预览、Registry、
源码交付），`templates/ainer-admin` 是其唯一完整模板与连接 Ainer Boot 的官方参考管理应用
（ADR-0022）。

2026-09-05，首个外部消费者完成了管理后台设计系统层的选型：`xq-platform-next` 的
`xq-admin-spike` 实测 Meta 开源的 Astryx（React 19 + StyleX，MIT，Meta 内部 8 年 /
13,000+ 应用血统，agent-first CLI），中文 locale 与后台 archetype（密集行 + 可调详情面板 +
PowerSearch）可用、构建通过。设计系统层选定为 Astryx。

维护者据此决策：终止 Studio 设计系统产品线——远端仓彻底删除、本地目录删除、dev 服务器
`/ainer-studio/` 路由与部署物下线。

## 决策驱动因素

- 设计系统层由 Astryx 承担后，自研设计系统产品失去消费者，继续维护是无预算的长期税；
- ainer-admin 的集成契约（browser public client、PKCE S256、audience 门禁、SDK 生成、
  同源反代、品牌 /login）已完整沉淀在本仓 `ainer-admin-integration.md` 与 ADR-0022，
  不依赖 Studio 仓存续；
- ADR-0054 的管理面 P5 退出门禁交付形态需要相应改道。

## 备选方案

### 方案 A：归档 GitHub 仓代替删除

零成本保留参考实现与历史，但产品线实质已死，归档仓会成为不再被打开的只读遗迹。维护者
明确选择彻底删除。不采用。

### 方案 B：Studio 重生为 Astryx 之上的策展层

保留"区块/模板/Registry"产品构想、更换设计底座。但当前没有第二个消费者证明该层有真实
需求，且 Astryx 自带 templates/blocks 能力。不采用。

### 方案 C：退役产品线，契约沉淀在本仓，参考实现冷备份

删除仓库与部署，把仍然有效的部分（集成契约、视觉合同引用）留在本仓文档体系内；
参考实现以维护者本地冷备份为唯一留存物，不作为可引用依赖。采用。

## 决策

1. **Studio 产品线退役**：Blocks、Templates、预览、Registry、源码交付全部停止；
   远端仓 `ainerlab/ainer-studio` 删除、本地工作目录删除、dev 服务器 `/ainer-studio/`
   nginx 路由与 `/opt/ainer-studio` 部署物下线。
2. **参考实现留存物**：`templates/ainer-admin` 源码唯一留存物为维护者本地冷备份
   `~/xq/archive/ainer-studio-final-20260905.tar.gz`（SHA256 前缀 `c67829b3…`）。
   它不是本仓制品、不构成可引用依赖；消费者实现管理面以本仓契约为准。
3. **契约继续有效**：`ainer-admin-integration.md` 与 ADR-0022 的契约继续作为
   消费者自建管理面的规范；品牌 `/login` 运行时由 Authorization Server 承载，不受退役影响。
4. **ADR-0054 管理面门禁改述**：P5 退出门禁从"参考管理台或 Initializer 管理面切片"
   改为"Initializer 管理面纵向切片生成，或存在至少一个消费者自建管理面通过
   OpenAPI SDK 契约门禁"。
5. **文档清理**：本仓所有把 Studio 描述为活动产品的表述改为退役表述；ADR-0022 加修订
   标记（不改写历史）。

## 后果

### 正面

- 停止一条无消费者的产品线，设计系统责任完全交给上游 Astryx；
- 管理面交付路线与首个消费者真实选型一致；
- 减少一个需要独立发版、部署和 nginx 路由的组件。

### 负面与风险

- `templates/ainer-admin` 不再是可运行参考实现；实现 xq-admin 认证层时只能依据契约文档
  与冷备份对照；
- 品牌登录页的视觉合同不再有源仓，后续改版需依据本仓引用与截图。

## 安全、数据与隐私

- 删除的是私有产品仓（无秘密入库，其纪律由 Studio 自身执行）；冷备份含源码与文档，
  仅存维护者本机 `~/xq/archive/`；
- dev 服务器下线 `/ainer-studio/` 路由减少一个公开暴露面；Authorization Server 与
  `/login` 不变。

## 运维与迁移

- 执行顺序：冷备份（已完成）→ 本 ADR 与文档修订合入 → 远端仓删除 → 本地目录删除 →
  dev nginx 移除 `location /ainer-studio/` 与 `/opt/ainer-studio` 下线并 reload；
- Authorization Server 的 `/login` 与 OAuth browser client 配置不在本 ADR 范围内，保持现状。

## 验收记录

- 选型证据：`xq-platform-next` 分支 `codex/astryx-admin-spike`（zh-CN locale 实测、
  后台 archetype、tsc/build 通过、真实浏览器交互验证）；
- 仓库删除与服务器下线按本 ADR 决策执行，执行记录见 `project-status.md`。

## 参考

- [ADR-0022](0022-ainer-admin-browser-integration-baseline.md)（被本 ADR 修订）
- [ADR-0054](0054-controlled-escape-hatch-and-open-distribution.md)（管理面门禁改述）
- [ADR-0004](0004-ainer-brand-and-naming-baseline.md)（命名基线，`Ainer Studio` 退出活动标识）
- [`../ainer-admin-integration.md`](../ainer-admin-integration.md)（继续有效的集成契约）
