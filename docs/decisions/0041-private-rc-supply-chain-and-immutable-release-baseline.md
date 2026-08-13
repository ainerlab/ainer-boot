# ADR-0041：私有 RC 供应链与不可变发布基线

- 状态：Accepted
- 日期：2026-08-13
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

`0.1.0-rc.1` 首次证明了 GitHub Actions 能用正式 OpenPGP 私钥签名并向 GitHub Packages 部署
Ainer reactor。然而这次发布也暴露了不能靠“deploy 步骤成功”代表发布合格的边界：

1. GitHub Packages 已接收制品后，GitHub Attestations 因私有仓库计费限制失败；
2. 为继续尝试，tag 指向了后续 workflow 修复提交，而已发布 Maven 制品来自更早源码提交；
3. 同版本重跑随即因 GitHub Packages `409 Conflict` 失败；
4. 没有远端空仓消费者、完整制品读回、checksum/SBOM/provenance release assets 或 GitHub Release。

因此 `0.1.0-rc.1` 只能证明一次签名 deploy 成功，不能作为 G2 合格 RC，也不能继续复用版本或移动
tag 来“补齐”证据。需要把版本、源码、签名、远端消费和发布证据收敛为单一失败关闭流程。

## 决策

### 1. 一个版本只绑定一个源码提交

- 发布只接受 `v<SemVer>` 的 annotated tag；workflow 必须验证 tag peel 后的 commit 与事件源码及当前
  默认分支头一致；
- deploy 前必须从 GitHub Packages 查询目标 BOM POM，目标版本已经存在时立即失败；
- 任何一次 deploy 开始后，该版本均视为已消耗。后续步骤失败时标记该版本为非合格/撤回，使用新的
  `rc.N+1` 修复，禁止覆盖、删除后重发或移动 tag；
- GitHub Release 必须在全部门禁通过后创建；仓库启用 Immutable Releases，并在创建后通过 release
  API 读回 `immutable=true`，使已发布 Release 对应 tag 与 assets 不可修改或删除。

### 2. 发布私钥必须有非空口令

- 正式私钥必须使用非空 passphrase；无口令私钥不得用于后续 RC 或稳定版；
- 正式私钥 fingerprint 必须由 repository variable 固定，导入 key 与期望 fingerprint 不一致时在
  deploy 前失败；
- 根 reactor 与 parentless BOM 的 Maven GPG Plugin 都启用 `bestPractices=true`，且只通过
  `MAVEN_GPG_PASSPHRASE` 环境变量读取口令；
- 禁止使用 `-Dgpg.passphrase=...`、日志输出、仓库文件或 release asset 传递口令；
- 导入后先用临时内容签名并验签，probe 失败时不得进入 deploy。

### 3. 远端制品必须完整读回验证

当前 reactor 有 23 个 project：3 个 `pom` project 与 20 个 `jar` project。Maven 4 同时发布 build
POM 与 consumer POM；20 个 JAR project 还发布 main/sources/Javadoc，Initializer CLI 另有 `cli`
classifier。因此每个版本有 **107 个主制品**，每个主制品都必须存在对应 detached ASCII-armored
OpenPGP 签名：

```text
23 × (build POM + consumer POM)
+ 20 × (main JAR + sources JAR + Javadoc JAR)
+ 1 × initializer CLI classifier JAR
= 107 primary artifacts + 107 .asc signatures
```

`scripts/release-artifacts.txt` 是发布制品面的唯一清单，合同测试必须将其与实际 reactor POM 对照。
workflow 必须用 GitHub Packages URL 逐个下载这 214 个文件，验证 107 个签名都来自仓库固定的精确
fingerprint，再基于远端主制品生成 SHA-256 与 SHA-512 清单。reactor 本地输出不能替代远端读回。

### 4. 远端消费者与本地消费者是两个门禁

- deploy 前保留本地 non-SNAPSHOT Maven 3.9+/Maven 4 Golden Consumer 和 Initializer 门禁；
- deploy 后，Maven 3 与 Maven 4 分别从全新的本地仓库解析远端 BOM/Starter；
- Initializer CLI 必须通过远端 classifier 坐标解析，再生成并验证普通、PostgreSQL 与 CRUD 消费者；
- 任何远端消费失败都使本版本非合格，不允许回退到 reactor install 或既有 `~/.m2` 缓存。

### 5. 强制项目签名 provenance，GitHub Attestations 是附加能力

每个合格发布必须将下列文件作为 Actions artifact 和 GitHub Release assets 保存：

- CycloneDX JSON SBOM；
- 远端 Maven 主制品的 SHA-256/SHA-512 清单；
- in-toto Statement 形状的 Ainer release provenance，记录版本、tag、精确源码 SHA、workflow run 和
  107 个主制品 digest；
- 发布制品清单、公钥、fingerprint、证据清单；
- 上述 SBOM、checksum、provenance、release notes 与证据清单的 OpenPGP 签名。

这是 **Ainer 项目签名的发布来源记录**，不宣称是 GitHub Attestation，也不宣称达到某个 SLSA 等级。
当仓库/计费支持 GitHub Attestations 时，可设置 `AINER_GITHUB_ATTESTATIONS=true` 启用附加 attestation；
一旦启用就必须失败关闭，不允许 `continue-on-error`。GitHub Attestations 不能替代上述必需证据。

### 6. `0.1.0-rc.1` 的处置

- GitHub Packages 中的签名制品来自 commit `77342b6`；
- 当前 `v0.1.0-rc.1` 指向后续 workflow 修复 commit `e7c653f`；
- 没有与这组制品绑定的 GitHub Release、完整远端签名读回、SBOM/checksum/provenance assets 和远端
  Golden Consumer 结果。

因此 `0.1.0-rc.1` 标记为 **withdrawn / non-qualifying**，不得作为产品依赖，也不得补发或重打。
下一次候选必须使用 `0.1.0-rc.2` 或更高的唯一版本。

## 备选方案

### 继续让 GitHub Attestations 非阻塞

拒绝。它会把“计费限制”与“来源证据通过”混成绿色发布，并让流程对实际缺口失真。

### 等仓库公开或升级计划后再做任何 provenance

拒绝。私有 RC 仍需要可离线核验的来源、digest 和签名；项目签名 provenance 可先建立独立证据链。

### 删除/移动 tag 或删除 package 后复用 `rc.1`

拒绝。消费者、缓存和审计记录可能已经观察到原版本；版本复用破坏不可变发布语义。

### 只抽查部分 JAR 的签名

拒绝。Maven 4 会发布 build POM 与 consumer POM，CLI 还有 classifier；抽查不能证明完整发布面。

## 后果

- 发布耗时增加，GitHub Packages 读回与两套空仓消费者会消耗更多 CI 时间；
- deploy 后的门禁失败会浪费一个版本号，但不会通过覆盖同版本隐藏不完整发布；
- 后续 signing secret 必须轮换为 passphrase-protected key；当前无口令 key 只保留用于验证已有
  `rc.1` 签名，不再用于新版本；
- GitHub Release 成为“合格发布”入口，单独存在的 Maven package version 不等于可消费 RC。

## 安全、数据与运维影响

- 私钥和口令不写入仓库、制品、命令行或日志；临时导入文件和 probe 在使用后删除；
- Release 只包含公钥与公开构建证据，不包含 PAT、私钥、口令、客户数据或供应商正文；
- GitHub Packages 认证失败、版本查询异常、签名 fingerprint 不匹配均失败关闭；
- 对 deploy 后失败的版本保留事实记录，不通过删除远端证据制造“从未发布”的假象。

## 实施记录

2026-08-13 已生成 passphrase-protected RSA-3072 certification primary 与独立 signing subkey；CI secret
只保存 signing subkey，完整加密备份保留在维护者受控本机，口令进入 macOS Keychain。正式 primary
fingerprint 为 `DC72A6994ABFA48B3D9B1DE145361DCB6F65F6FD`，已写入 repository variable；根 reactor、
parentless BOM 与隔离签名 probe 均通过。该记录只证明签名身份配置完成，不替代 tag workflow 与远端
制品证据。

2026-08-13 annotated tag `v0.1.0-rc.2`、默认分支与 immutable GitHub Release 已精确绑定到 commit
`0f99ee08f5d9145bc5bc72052eaf59774aad8054`。release run `31666957663` 完成 336/0/0/0、107 个
主制品与 107 个签名远端读回、精确 fingerprint 验证、Maven 3/Maven 4 与 Initializer 空仓消费、
签名 SBOM/checksum/107-subject provenance 和 16 个 immutable Release assets；发布后隔离回读的
7 个 detached signatures 与 14 项 evidence checksums 也全部通过。该记录满足下方验收第 4 项，
不替代第 5 项的真实产品消费、migration replay、升级与回滚。

## 验收方式

1. `scripts/check-release-contracts.sh` 拒绝硬编码 `ab` 路径、GPG 命令行口令和非阻塞 attestation；
2. `scripts/verify-release-ref.sh` 对 annotated tag/source 做正负验证；
3. 本地 `./mvnw clean verify`、Maven 3/4 consumer、Initializer consumer 全绿；
4. 新的唯一 RC workflow 成功完成 package absence、签名 deploy、`0 skipped`、远端空仓消费、
   107/107 读回验签、SBOM/checksum/provenance 和 immutable GitHub Release；
5. 产品从远端固定该 RC，完成 migration replay、升级与回滚后，G2 才可关闭。

第 4 项已由 `v0.1.0-rc.2` 的远端发布记录证明；第 5 项仍必须由真实产品消费记录证明。本 ADR
Accepted、本地代码就绪或通用空仓 consumer 本身均不构成 G2 完成证据。

## 参考

- [ADR-0026：Maven 4 构建与 Consumer POM 基线](0026-maven-4-build-and-consumer-pom-baseline.md)
- [ADR-0040：P3 企业基座与 1.0 产品契约](0040-p3-enterprise-base-and-1.0-product-contract.md)
- [版本与发布规范](../releasing.md)
- [当前项目状态](../project-status.md)
