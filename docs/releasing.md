# Ainer 版本与发布规范

> 文档类型：长期维护规范 · 状态：生效 · 最近核对：2026-08-26 · 适用版本：`0.1.x`

## 1. 版本与资格语义

Ainer 使用语义化版本 `MAJOR.MINOR.PATCH`；预发布使用 `-alpha.N`、`-beta.N`、`-rc.N`。正式发布
不得包含 `SNAPSHOT`。

- `PATCH`：兼容性修复、文档或内部优化；
- `MINOR`：向后兼容的新能力；`0.x` 阶段的边界变化必须在 Changelog 明示；
- `MAJOR`：稳定期后的破坏性 API、配置或数据契约变化。

同一版本只能绑定一个源码 commit。GitHub Packages 中出现某个版本，只说明 registry 已接收过制品；
只有对应 GitHub Release 存在、全部门禁通过且发布证据完整，才是合格发布。deploy 开始后的失败版本
必须标记 withdrawn/non-qualifying，并使用新的版本修复，禁止删除、覆盖或移动 tag 后复用版本。

## 2. 兼容性维度

发布说明必须分别评估 Java API、HTTP API、错误码、JWT claims/scopes、配置键、数据库
schema/migration、事件 payload、Starter 自动配置、Initializer manifest、商业授权和运维方式。某一
维度兼容不代表整体兼容。

事件 payload 必须带版本；消费者按事件 ID 幂等。配置或 API 弃用应至少跨一个计划发布窗口，并在
Changelog 提供替代方案。Stable/Incubating/非目标边界以 ADR-0040 为准。

## 3. 发布候选门禁

1. `project-status.md` 已更新实际范围、未完成项和证据边界；
2. `CHANGELOG.md` 的 `Unreleased` 已整理到目标版本；
3. 相关 ADR 已接受，许可证台账已更新；Ainer 源码许可为 MIT（ADR-0051），根目录
   `LICENSE`/`NOTICE` 与根 POM `<licenses>` 必须与之一致；商标仍按 ADR-0004，不因
   MIT 而宣称已取得商标；
4. JDK 25 下 `./mvnw clean verify` 通过，PostgreSQL Testcontainers 为 `0 skipped`；
5. 本地 non-SNAPSHOT Maven 3.9+/Maven 4 Golden Consumer 与 Initializer 三通道通过；
6. Maven Artifact Plugin 的 build plan 与可重复构建比较通过；
7. 两个可执行 JAR 在目标 JDK 启动；
8. 空库 migration、升级 migration、关键约束、事务回滚和上一版应用兼容性通过；
9. issuer、audience、key、secret 注入和 Actuator exposure 已审查；
10. 401/403、Workspace 跨边界、AI 预算/脱敏等安全 smoke 通过；
11. 备份恢复与应用回滚在非生产环境演练；
12. annotated tag 精确指向默认分支头，目标源码 SHA、版本不存在性与 GitHub Immutable Releases
    声明通过预检；
13. deploy 后远端 Maven 3/4 空仓消费、远端 Initializer、107 个主制品与 107 个 `.asc` 读回通过；
14. CycloneDX SBOM、SHA-256/SHA-512、项目签名 provenance、公钥与证据签名已成为 GitHub Release assets。

其中第 13、14 项必须针对 registry 中的实际字节执行，本地 reactor/install 结果不能替代。

## 4. 本地构建与消费验证

生产者构建、安装和发布只使用仓库 Maven Wrapper（JDK 25 + Maven 4.0.0-rc-6 preview）。系统
Maven 3.9+ 只由 consumer 脚本验证下游兼容，不能构建或发布 Ainer reactor。

```bash
export AINER_VERSION='<目标版本>'
./mvnw --version
./mvnw -Drevision="$AINER_VERSION" artifact:check-buildplan
AINER_REPRO_REPOSITORY="$(mktemp -d)"
./mvnw -Drevision="$AINER_VERSION" -Dgpg.skip=true \
  -Dmaven.repo.local="$AINER_REPRO_REPOSITORY" clean install
./mvnw -Drevision="$AINER_VERSION" -Dgpg.skip=true \
  -Dmaven.repo.local="$AINER_REPRO_REPOSITORY" clean verify artifact:compare
AINER_VERSION="$AINER_VERSION" ./scripts/verify-maven-consumers.sh
AINER_VERSION="$AINER_VERSION" ./scripts/verify-initializer-consumer.sh
./scripts/check-release-contracts.sh
git diff --check
git status --short --branch
```

脚本默认使用 `AINER_ARTIFACT_SOURCE=local`，在隔离仓库安装 producer 后验证 28 个 consumer POM、
公开配置元数据、sources/Javadoc、Maven 3/4 Golden Consumer 和 Initializer。Initializer 的普通、
PostgreSQL 与 CRUD 三通道必须执行各自生成的 Maven 3.9.16 Wrapper。发布后的远端门禁使用：

```bash
export AINER_ARTIFACT_SOURCE=remote
export AINER_MAVEN_SETTINGS='<只含远端仓库和 read:packages 凭据的 settings.xml>'
AINER_VERSION="$AINER_VERSION" ./scripts/verify-maven-consumers.sh
AINER_VERSION="$AINER_VERSION" ./scripts/verify-initializer-consumer.sh
```

远端模式拒绝 SNAPSHOT 和缺失 settings；Maven 3、Maven 4 分别从空本地仓库解析，Initializer CLI
也通过远端 `cli` classifier 获取。不得预装 reactor 或复用开发者 `~/.m2` 充当远端消费证据。

## 5. GitHub Packages 发布流程

发布坐标为 `https://maven.pkg.github.com/ainerlab/ainer-boot`，repository id 为
`github-packages`。2026-08-26 核对：既有 26 个 Maven 包均为 public（`0.1.0` /
`0.2.0` / `1.0.0`）。新包在首次 deploy 后仍应核对其 `visibility`，不得假设继承仓库
公开状态。`.github/workflows/release.yml` 只由 `v*` tag 触发，顺序固定为：

1. checkout 完整历史，验证 tag 是 annotated SemVer tag，且 peel 后 commit 同时等于 workflow 源码与
   当前默认分支头；
2. 检查 `AINER_IMMUTABLE_RELEASES=true` 声明，并在 GitHub Packages 查询 BOM POM；版本存在即停止；
3. 运行 shell/release 契约、Docker、锁定 Maven 3.9.16、本地 non-SNAPSHOT consumers，以及使用
   生成项目自身 Wrapper 的 Initializer 三通道；
4. 导入 passphrase-protected GPG key，执行一次临时签名与验签 probe；
5. 使用 `-Prelease clean deploy` 完整测试、附加 sources/Javadoc、签名并部署；
6. 强制 Surefire failure/error/skipped 全为零，生成 CycloneDX release SBOM；
7. 下载 107 个 Maven 主制品及 107 个 `.asc`，带重试逐一校验精确 fingerprint，生成
   checksum/provenance；
8. 确认远端制品完整可见后，从两个空仓运行 Maven 3/4 consumer，并从远端获取 CLI 生成带 Wrapper
   的项目，再以生成 Wrapper 运行 Initializer 三通道；
9. 上传签名发布证据；若启用 GitHub Attestations，attestation 也必须成功；
10. 最后创建 GitHub Release，并从 release API 读回 `immutable=true`；否则本版本不合格。

任何一步失败都不能通过重跑同版本 deploy 修补。若失败发生在 deploy 前，可以修复代码后创建新的
tag；若 deploy 已开始或 registry 中已出现该版本，必须递增 `rc.N`。

### 5.1 仓库配置

必需配置：

- repository variable `AINER_RELEASE_SIGNING=true`；
- repository variable `AINER_IMMUTABLE_RELEASES=true`，且仓库设置实际为 enabled；
- repository variable `AINER_RELEASE_GPG_FINGERPRINT`：40 位正式发布 key fingerprint；
- secret `AINER_GPG_KEY_BASE64`：ASCII-armored 私钥的 base64；
- secret `AINER_GPG_PASSPHRASE`：非空口令；
- GitHub Immutable Releases：enabled；
- workflow `GITHUB_TOKEN`：`contents:write`、`packages:write`。

可选 repository variable `AINER_GITHUB_ATTESTATIONS=true` 只在仓库/计费支持 GitHub
Attestations 时启用。未启用时，项目签名 provenance 仍是强制门禁；启用后 GitHub attestation
失败必须阻断，不允许 `continue-on-error`。

本地手动发布需要在 Maven settings 为 `github-packages` 配置 `write:packages` 凭据，但常规发布只走
tag workflow。既有 26 个包已是 public，可匿名拉取。不得把 PAT 写入 POM、仓库或日志。

## 6. 签名、制品清单与 provenance

根 reactor 与 parentless `ainer-dependencies` 都在 `release` profile 使用 Maven GPG Plugin：

- `bestPractices=true`；
- `passphraseEnvName=MAVEN_GPG_PASSPHRASE`；
- 导入私钥的 fingerprint 必须与 `AINER_RELEASE_GPG_FINGERPRINT` 精确匹配；
- passphrase 不出现在 Maven CLI、POM、日志或 release assets；
- 无口令私钥、缺 key、缺 passphrase 或 probe 失败均在 deploy 前关闭发布。

Maven 4 的 28 个 project 同时发布 build POM 与 consumer POM；JAR project 发布
main/sources/Javadoc，Initializer CLI 另有 classifier，共 132 个主制品。唯一清单维护在
`scripts/release-artifacts.txt`，合同测试会将其与实际 reactor POM 逐项比较；
`verify-remote-release-artifacts.sh` 必须从 GitHub Packages 读回并验证全部 132 个签名，而非抽查。

强制 release assets 包含：CycloneDX SBOM、`MAVEN-SHA256SUMS`、`MAVEN-SHA512SUMS`、Ainer
release provenance、制品清单、公钥/fingerprint、证据 checksum 与各自 `.asc`。Ainer provenance 是项目签名的
in-toto Statement 形状来源记录，不等于 GitHub Attestation，也不代表 SLSA 等级认证。详细决策见
[ADR-0041](decisions/0041-private-rc-supply-chain-and-immutable-release-baseline.md)。

## 7. 发布记录与验证边界

发布记录至少保存：源码 commit、tag/版本、workflow run、JDK/Maven、测试摘要、local/remote consumer、
migration/回滚、132 个主制品 checksum、签名 fingerprint、SBOM、provenance 和批准人。

以下表述必须区分：

- `deploy success`：registry 接收了制品；
- `remote consumer success`：空仓消费者解析并运行成功；
- `qualifying RC`：所有门禁和 immutable GitHub Release 完成；
- `stable release`：还需真实产品纵向切片、升级、migration replay 与回滚证据。

## 8. 数据库发布、回滚与停止条件

- 发布前从备份副本验证升级，不在生产第一次执行；
- 评估 DDL 锁、回填时长、磁盘增长和上一版应用兼容性；
- 破坏性变更使用 expand-contract；
- migration 成功但应用失败时，优先回滚到仍兼容 schema 的应用版本；
- 不依赖自动 down migration，不修改 Flyway history 伪造恢复。

遇到 migration 不一致、鉴权绕过、数据隔离破坏、秘密泄露、签名不匹配、tag/source 不一致、
不可解释的数据损坏或无法恢复的事件积压时立即停止发布。回滚前先保存诊断资料；新 migration 若
不向后兼容，不能只回滚 JAR，必须执行该版本事先定义的恢复方案。

## 9. 发布后

- 从 GitHub Release 而不是孤立 package version 选择消费版本；
- 验证 health、Token 签发、JWT 校验和关键业务 smoke；
- 观察错误率、延迟、数据库连接/锁、归档积压和 AI provider 失败；
- 将实际 migration replay、产品消费、升级与回滚结果写入发布记录；
- 清空 Changelog 的 `Unreleased` 时立即建立新的空区段；
- 发现设计偏差时新增 ADR 或修复文档，不让口头结论成为长期规则。
