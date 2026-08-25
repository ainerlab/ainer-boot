# ADR-0051：Ainer Boot 采用 MIT，仓库公开

- 状态：Accepted
- 日期：2026-08-25
- 决策者：Ainer 项目维护者
- 取代：无
- 修订：[ADR-0041](0041-private-rc-supply-chain-and-immutable-release-baseline.md)
  的「私有分发」假设；[ADR-0048](0048-packages-storage-governance-and-rc-artifacts-retirement.md)
  中「免费版私有仓库存储配额」作为当前阻塞的前提。不改写 ADR-0004 商标/命名结论，
  也不改写 ADR-0041 的签名、SBOM、provenance 与不可变 Release 门禁。
- 被取代：无

## 背景

2026-08-04 的工程记录将许可证定为「暂不开源（私有/专有）」，公开发行另议。
该决定让 GitHub 免费版把 Actions 分钟数与 Packages 存储算进私有配额：`v1.1.0`
远端发布与后续 CI 因存储配额秒失败，不是代码门禁失败。

维护者决定公开 `ainerlab/ainer-boot`，源码许可采用 MIT，让质量门禁和制品分发
回到可运行状态，并让外部消费者按标准开源条款使用脚手架。

## 决策驱动因素

- 质量门禁必须在 GitHub Actions 上可重复执行，不能长期用本机 `./mvnw` 代替远端证据；
- 源码许可必须可被 Maven Central / GitHub Packages 与下游 POM 声明；
- 品牌与商标不得被理解成「MIT 等于可以使用 Ainer 名义做衍生商品」；
- 已接受的供应链门禁（签名、SBOM、读回、不可变 Release）不因公开而放松。

## 备选方案

### 方案 MIT + 公开仓库（采用）

根目录 `LICENSE` 使用 MIT；GitHub 仓库 visibility 改为 public。第三方依赖仍按各自
许可证与 [`dependencies.md`](../dependencies.md) / Release SBOM 披露。

### 方案 Apache-2.0（未选）

对专利授权与 NOTICE 有更强默认要求。当前没有需要单独专利授权条款的贡献者协议，
MIT 更短、与多数 Java 脚手架下游预期一致。第三方 Apache-2.0 依赖不受本仓库许可替换。

### 方案 继续私有 + 付费额度（未选）

能保住私有分发，但每月额度仍是人工运维，不能解除「CI 因配额秒红」对流程的阻塞。
维护者已明确要求公开仓库。

## 决策

1. **Ainer 自己撰写的源码、文档与由本仓库发布的 Ainer 制品**，自本 ADR 生效的
   commit 起按 **MIT** 许可。版权行使用 `Copyright (c) 2026 Ainer contributors`。
2. **GitHub 仓库 `ainerlab/ainer-boot` 改为 public**。公开后 GitHub 免费版的
   Actions 公共仓库额度与公开 Packages 存储规则适用；既有私有 package 版本的
   可见性需在 GitHub Packages 侧单独核对，不得假设一改仓库就自动变成公开坐标。
3. **MIT 不授予商标权。** `Ainer` 名称、标识与产品命名仍以
   [ADR-0004](0004-ainer-brand-and-naming-baseline.md) 为准：未完成商标注册前不得
   对外宣称已拥有商标；衍生发行物不得暗示官方背书。
4. **供应链门禁保持。** 签名 deploy、SBOM、checksum、provenance、不可变 GitHub
   Release 与「同一版本只绑定一个 commit」仍按 ADR-0041。公开只改变分发可见性，
   不把未签名快照宣称为合格发布。
5. **Initializer 生成的下游项目不自动继承本 LICENSE。** 生成树是消费者自己的作品，
   许可由消费者决定；模板中的 Apache Maven Wrapper 等上游文件保持其原许可页眉。
6. **Community / Pro / Enterprise 分层、定价与付费支持仍是草案**，不因 MIT 而变成
   已承诺的商业合同。

## 后果

### 正面

- 公开仓库后，标准 GitHub-hosted Actions 不再消耗私有分钟数配额；公开 Maven
  包不再计入私有 Packages 存储（以 GitHub 当时计费规则为准）；
- 免费版可启用分支保护等在私有仓库上被锁住的治理能力；
- 下游可合法复制、修改、再分发 Ainer 源码，只需保留版权与许可声明。

### 负面与风险

- 完整 git 历史一并公开，必须继续禁止把密钥、客户数据、prompt 正文写入仓库；
- 商标未注册，公开后更需要在 README/NOTICE 写清「许可 ≠ 商标授权」；
- 既有 `v1.0.0` / `v1.1.0` 等 tag 在公开前按私有/专有分发；公开后这些 tag 的
  **源码**同样按 MIT 可读，但当时 GitHub Release 资产的 Packages 可见性仍以
  registry 记录为准。

## 安全、数据与隐私

公开前不放宽文档纪律：示例与日志规范仍禁止真实密码、Token、私钥、API key、
客户数据、prompt 或供应商正文。GitHub Actions secrets 与 Packages 写权限仍只
存在于仓库设置，不进入 git。

## 运维与迁移

- 根 POM 声明 `<licenses>` 为 MIT，供消费 POM / SBOM 读取；
- 将仓库设为 public 后，视需要把后续 deploy 的 GitHub Packages 可见性改为
  public，并重跑被配额打断的质量门禁；
- ADR-0048 的 rc 制品退役纪律仍然有用，不因公开而恢复已删除的 registry 文件。

## 验收记录

- 根目录存在 `LICENSE`（MIT）与 `NOTICE`（商标与第三方指向）；
- ADR 索引、README、`project-status.md`、`handoff.md`、`releasing.md` 与商业文档
  的**当前**许可/可见性表述与本 ADR 一致；历史条目保留「当时暂不开源」的事实。

## 参考

- [MIT License](https://opensource.org/licenses/MIT)
- [ADR-0004](0004-ainer-brand-and-naming-baseline.md)
- [ADR-0041](0041-private-rc-supply-chain-and-immutable-release-baseline.md)
- [ADR-0048](0048-packages-storage-governance-and-rc-artifacts-retirement.md)
