package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import dev.ainer.initializer.manifest.ManifestFixture;
import dev.ainer.initializer.manifest.ManifestReader;
import dev.ainer.initializer.manifest.ManifestV1;
import dev.ainer.initializer.preview.ProjectDiffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectGeneratorTest {

    @TempDir
    Path tempDir;

    private ProjectTree generate(ManifestV1 manifest) {
        return new ProjectGenerator(manifest).generate();
    }

    @Test
    @DisplayName("同 manifest 两次生成字节级一致（golden determinism）")
    void generationIsDeterministic() throws IOException {
        ManifestV1 manifest = ManifestFixture.sample();

        ProjectTree first = generate(manifest);
        ProjectTree second = generate(manifest);

        assertThat(first.files()).hasSameSizeAs(second.files());
        for (int i = 0; i < first.files().size(); i++) {
            GeneratedFile a = first.files().get(i);
            GeneratedFile b = second.files().get(i);
            assertThat(a.path()).isEqualTo(b.path());
            assertThat(a.content()).isEqualTo(b.content());
            assertThat(a.executable()).isEqualTo(b.executable());
        }
    }

    @Test
    @DisplayName("生成的文件清单齐全且路径合法")
    void generatesExpectedFileList() throws IOException {
        ProjectTree tree = generate(ManifestFixture.sample());

        List<String> paths = tree.files().stream().map(GeneratedFile::path).toList();
        assertThat(paths).containsExactly(
                ".gitignore",
                ".mvn/wrapper/maven-wrapper.properties",
                "README.md",
                "mvnw",
                "mvnw.cmd",
                "pom.xml",
                "src/main/java/dev/ainer/consumer/sample/SampleProjectApplication.java",
                "src/main/java/dev/ainer/consumer/sample/ping/PingController.java",
                "src/main/resources/application.yml",
                "src/test/java/dev/ainer/consumer/sample/SampleProjectApplicationSmokeTest.java");
    }

    @Test
    @DisplayName("生成项目自带固定版本和摘要的 Maven Wrapper")
    void generatesPinnedMavenWrapper() throws IOException {
        ProjectTree tree = generate(ManifestFixture.sample());

        GeneratedFile shellWrapper = tree.files().stream()
                .filter(file -> file.path().equals("mvnw"))
                .findFirst()
                .orElseThrow();
        GeneratedFile windowsWrapper = tree.files().stream()
                .filter(file -> file.path().equals("mvnw.cmd"))
                .findFirst()
                .orElseThrow();
        String properties = tree.files().stream()
                .filter(file -> file.path().equals(".mvn/wrapper/maven-wrapper.properties"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();

        assertThat(shellWrapper.executable()).isTrue();
        assertThat(windowsWrapper.executable()).isFalse();
        assertThat(shellWrapper.utf8()).contains("Apache Maven Wrapper startup batch script, version 3.3.4");
        assertThat(properties).contains(
                "wrapperVersion=3.3.4",
                "distributionType=only-script",
                "apache-maven/3.9.16/apache-maven-3.9.16-bin.zip",
                "distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce");
    }

    @Test
    @DisplayName("写入后在 POSIX 文件系统保留 Wrapper 执行位且 diff 检测模式漂移")
    void writesAndDiffsWrapperExecutionMode() throws IOException {
        ProjectTree tree = generate(ManifestFixture.sample());
        Path target = tempDir.resolve("wrapper-mode");
        new ProjectWriter().write(tree, target, false);
        Path wrapper = target.resolve("mvnw");

        PosixFileAttributeView posix = Files.getFileAttributeView(wrapper, PosixFileAttributeView.class);
        if (posix == null) {
            return;
        }
        assertThat(Files.isExecutable(wrapper)).isTrue();
        Files.setPosixFilePermissions(wrapper, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ));

        ProjectDiffer.DiffResult diff = new ProjectDiffer().diff(tree, target);
        assertThat(diff.modifiedFiles()).containsExactly("mvnw");
    }

    @Test
    @DisplayName("pom.xml 引用 BOM import 与隐含 web starter")
    void pomReferencesBomAndWebStarter() throws IOException {
        GeneratedFile pom = generate(ManifestFixture.sample()).files().stream()
                .filter(f -> f.path().equals("pom.xml"))
                .findFirst()
                .orElseThrow();

        String content = pom.utf8();
        assertThat(content).contains(
                "<artifactId>sample-project</artifactId>",
                "<groupId>dev.ainer</groupId>",
                "<artifactId>ainer-dependencies</artifactId>",
                "<version>0.1.0</version>",
                "<artifactId>ainer-starter-web</artifactId>",
                "<java.version>25</java.version>");
    }

    @Test
    @DisplayName("extra starter 与 postgres 变体写入 pom 与配置")
    void postgresVariantAddsDependencies() throws IOException {
        ManifestV1 pg = ManifestFixture.postgres();
        ProjectTree tree = generate(pg);

        String pom = tree.files().stream()
                .filter(f -> f.path().equals("pom.xml"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(pom).contains("org.postgresql", "postgresql", "testcontainers");
        assertThat(pom).doesNotContain("serverTimezone");

        String config = tree.files().stream()
                .filter(f -> f.path().equals("src/main/resources/application.yml"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(config).contains("datasource", "DATASOURCE_URL");
        assertThat(config).contains("virtual", "enabled: true");
    }

    @Test
    @DisplayName("所有变体默认开启虚拟线程（ADR-0029 决策 5）")
    void generatedProjectsEnableVirtualThreadsByDefault() throws IOException {
        for (ManifestV1 manifest : java.util.List.of(
                ManifestFixture.sample(), ManifestFixture.postgres())) {
            ProjectTree tree = generate(manifest);
            String config = tree.files().stream()
                    .filter(f -> f.path().equals("src/main/resources/application.yml"))
                    .map(GeneratedFile::utf8)
                    .findFirst()
                    .orElseThrow();
            assertThat(config).contains("threads", "virtual", "enabled: true")
                    .as("generated project must enable virtual threads by default");
        }
    }

    @Test
    @DisplayName("postgres 变体生成 Testcontainers 集成测试且演绎 persistence starter")
    void postgresVariantAddsTestcontainersSmokeTest() throws IOException {
        ManifestV1 pg = ManifestFixture.postgres();
        ProjectTree tree = generate(pg);

        String pom = tree.files().stream()
                .filter(f -> f.path().equals("pom.xml"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(pom).contains("ainer-starter-persistence");

        String test = tree.files().stream()
                .filter(f -> f.path().endsWith("ApplicationSmokeTest.java"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(test).contains(
                "@Testcontainers",
                "PostgreSQLContainer",
                "AinerPostgresContainer.create()",
                "@ServiceConnection",
                "AutoConfigureTestRestTemplate",
                "DataSource");
    }

    @Test
    @DisplayName("普通变体不生成 Testcontainers 测试")
    void plainVariantHasNoTestcontainers() throws IOException {
        ProjectTree tree = generate(ManifestFixture.sample());

        String test = tree.files().stream()
                .filter(f -> f.path().endsWith("ApplicationSmokeTest.java"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(test).doesNotContain("Testcontainers").doesNotContain("DataSource");
    }

    @Test
    @DisplayName("生成项目包含 actuator health 最小暴露与健康测试")
    void actuatorHealthIsGenerated() throws IOException {
        ProjectTree tree = generate(ManifestFixture.sample());

        String pom = tree.files().stream()
                .filter(f -> f.path().equals("pom.xml"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(pom).contains("spring-boot-starter-actuator");

        String config = tree.files().stream()
                .filter(f -> f.path().equals("src/main/resources/application.yml"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(config).contains("management:", "include: health");

        String test = tree.files().stream()
                .filter(f -> f.path().endsWith("ApplicationSmokeTest.java"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(test).contains("/actuator/health")
                .contains("\\\"status\\\":\\\"UP\\\"");
    }

    @Test
    @DisplayName("postgres 变体与显式 persistence starter 不重复生成依赖")
    void postgresVariantDeduplicatesPersistenceStarter() throws IOException {
        ManifestV1 manifest = new ManifestReader().read(string("""
                schemaVersion: v1
                project:
                  name: dup
                  groupId: dev.ainer.consumer
                  artifactId: dup
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 25
                database: postgresql
                starters:
                  - dev.ainer:ainer-starter-persistence
                """));

        ProjectTree tree = generate(manifest);
        String pom = tree.files().stream()
                .filter(f -> f.path().equals("pom.xml"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(pom).containsOnlyOnce("<artifactId>ainer-starter-persistence</artifactId>");
    }

    @Test
    @DisplayName("owner 进入 README 而不进入运行配置")
    void ownerOnlyInReadme() throws IOException {
        ManifestV1 pg = ManifestFixture.postgres();
        ProjectTree tree = generate(pg);

        String readme = tree.files().stream()
                .filter(f -> f.path().equals("README.md"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(readme).contains("Ainer Team", "team@example.com");

        String config = tree.files().stream()
                .filter(f -> f.path().equals("src/main/resources/application.yml"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
        assertThat(config).doesNotContain("Ainer Team").doesNotContain("team@example.com");
    }

    @Test
    @DisplayName("预览不写入磁盘且只读")
    void previewDoesNotWrite() throws IOException {
        ManifestV1 manifest = ManifestFixture.sample();
        long before = countBytes(tempDir);
        generate(manifest);
        long after = countBytes(tempDir);
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("目标非空拒绝覆盖（无 force 时）")
    void refusesNonEmptyTarget() throws IOException {
        ManifestV1 manifest = ManifestFixture.sample();
        Path target = Files.createDirectories(tempDir.resolve("existing"));
        Files.writeString(target.resolve("keep.txt"), "keep");

        ProjectWriter writer = new ProjectWriter();
        assertThatThrownBy(() -> writer.write(generate(manifest), target, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode())
                        .isEqualTo(InitializerErrorCode.UNSUPPORTED_TARGET));
        assertThat(Files.readString(target.resolve("keep.txt"))).isEqualTo("keep");
    }

    @Test
    @DisplayName("force 允许覆盖生成文件但保留外部文件")
    void forceOverwritesGeneratedFiles() throws IOException {
        ManifestV1 manifest = ManifestFixture.sample();
        Path target = Files.createDirectories(tempDir.resolve("forced"));
        Files.writeString(target.resolve("keep.txt"), "keep");

        new ProjectWriter().write(generate(manifest), target, true);

        assertThat(target.resolve("pom.xml")).isRegularFile();
        assertThat(Files.readString(target.resolve("keep.txt"))).isEqualTo("keep");
    }

    @Test
    @DisplayName("diff 正确识别新增、修改与不变")
    void diffClassifiesFiles() throws IOException {
        ManifestV1 manifest = ManifestFixture.sample();
        Path target = Files.createDirectories(tempDir.resolve("diffed"));
        new ProjectWriter().write(generate(manifest), target, false);
        Files.writeString(target.resolve("README.md"), "tampered");

        ProjectDiffer.DiffResult result = new ProjectDiffer().diff(generate(manifest), target);

        assertThat(result.newFiles()).isEmpty();
        assertThat(result.modifiedFiles()).containsExactly("README.md");
        assertThat(result.unchangedFiles()).contains("pom.xml");
    }

    @Test
    @DisplayName("生成后写入磁盘再 diff 无变更")
    void replayAfterWriteIsStable() throws IOException {
        ManifestV1 manifest = ManifestFixture.sample();
        Path target = tempDir.resolve("replay");
        new ProjectWriter().write(generate(manifest), target, false);

        ProjectDiffer.DiffResult diff = new ProjectDiffer().diff(generate(manifest), target);
        assertThat(diff.hasChanges()).isFalse();
    }

    @Test
    @DisplayName("crud 变体生成 6 类 CRUD 文件且 deterministic")
    void crudVariantGeneratesCrudFiles() throws IOException {
        ManifestV1 crud = ManifestFixture.crud();
        ProjectTree tree = generate(crud);
        ProjectTree replay = generate(crud);

        assertThat(tree.files()).hasSameSizeAs(replay.files());
        List<String> paths = tree.files().stream().map(GeneratedFile::path).toList();
        assertThat(paths).contains(
                "src/main/resources/db/migration/V1__init.sql",
                "src/main/java/dev/ainer/consumer/crud/crud/ProductEntity.java",
                "src/main/java/dev/ainer/consumer/crud/crud/ProductMapper.java",
                "src/main/java/dev/ainer/consumer/crud/crud/ProductApplicationService.java",
                "src/main/java/dev/ainer/consumer/crud/crud/ProductController.java",
                "src/test/java/dev/ainer/consumer/crud/crud/ProductCrudIntegrationTest.java");
    }

    @Test
    @DisplayName("crud migration 使用 uuidv7 主键与参数化 DDL")
    void crudMigrationUsesUuidv7AndComments() throws IOException {
        String sql = generate(ManifestFixture.crud()).files().stream()
                .filter(f -> f.path().equals("src/main/resources/db/migration/V1__init.sql"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();

        assertThat(sql).contains("CREATE TABLE ainer_product (");
        assertThat(sql).contains("id uuid PRIMARY KEY DEFAULT uuidv7()");
        assertThat(sql).contains("sku varchar(32) NOT NULL");
        assertThat(sql).contains("price numeric(19,4) NULL");
        assertThat(sql).contains("published_at timestamptz NOT NULL");
        assertThat(sql).contains("ref_id uuid NOT NULL");
        assertThat(sql).contains("CONSTRAINT uq_ainer_product_sku UNIQUE (sku)");
        assertThat(sql).contains("COMMENT ON COLUMN ainer_product.name IS '产品名称'");
        assertThat(sql).doesNotContain("{{");
    }

    @Test
    @DisplayName("生成实体类拥有字段、访问器与保留时间戳列")
    void crudEntityHasFieldsAndAccessors() throws IOException {
        String entity = file(generate(ManifestFixture.crud()), "ProductEntity.java");
        assertThat(entity).contains("@TableName(\"ainer_product\")")
                .contains("private String sku;")
                .contains("private BigDecimal price;")
                .contains("private Instant publishedAt;")
                .contains("private UUID refId;")
                .contains("public String getSku()")
                .contains("public void setSku(String sku)")
                .contains("private Instant createdAt;")
                .contains("import java.math.BigDecimal;")
                .doesNotContain("UUID.randomUUID()");
    }

    @Test
    @DisplayName("生成的 Mapper 使用 RETURNING id 而非应用侧 UUID")
    void generatedMapperUsesReturningId() throws IOException {
        String mapper = generate(ManifestFixture.crud()).files().stream()
                .filter(f -> f.path().endsWith("ProductMapper.java"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();

        assertThat(mapper).contains("@Mapper")
                .contains("extends BaseMapper<ProductEntity>")
                .contains("INSERT INTO ainer_product (name, sku, price, active, published_at, ref_id, created_at, updated_at)")
                .contains("VALUES (#{name}, #{sku}, #{price}, #{active}, #{publishedAt}, #{refId}, #{createdAt}, #{updatedAt}) RETURNING id")
                .doesNotContain("${");
    }

    @Test
    @DisplayName("生成的 Service 幂等创建且资源缺失抛 NOT_FOUND")
    void generatedServiceUsesBoundQueries() throws IOException {
        String service = generate(ManifestFixture.crud()).files().stream()
                .filter(f -> f.path().endsWith("ProductApplicationService.java"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();

        assertThat(service).contains("BusinessException(StandardErrorCode.NOT_FOUND")
                .contains("insertReturningId(row)")
                .contains("public ProductEntity create(ProductEntity row)")
                .contains("public void delete(UUID id)")
                .doesNotContain("UUID.randomUUID()");
    }

    @Test
    @DisplayName("Controller 全部端点使用 ApiResponse 与 X-Request-Id")
    void generatedControllerUsesEnvelope() throws IOException {
        String controller = generate(ManifestFixture.crud()).files().stream()
                .filter(f -> f.path().endsWith("ProductController.java"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();

        assertThat(controller).contains("@RequestMapping(\"/api/products\")")
                .contains("ApiResponse.success")
                .contains("RequestIds.currentOrCreate(request)")
                .contains("@PostMapping", "@GetMapping", "@PutMapping", "@DeleteMapping")
                .doesNotContain("@PreAuthorize");
    }

    @Test
    @DisplayName("CRUD 集成测试覆盖 create→get→update→list→delete 全链路")
    void crudIntegrationTestCoversLifecycle() throws IOException {
        String test = generate(ManifestFixture.crud()).files().stream()
                .filter(f -> f.path().endsWith("ProductCrudIntegrationTest.java"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();

        assertThat(test).contains("@Testcontainers", "PostgreSQLContainer", "AinerPostgresContainer.create()")
                .contains("JsonPath.read");
        assertThat(test).contains("isEqualTo(201)");
        assertThat(test).contains("isEqualTo(404)");
        assertThat(test).contains("client.putJson");
    }

    @Test
    @DisplayName("CRUD 集成测试示例值不超过 string(N) 长度上限")
    void crudIntegrationTestSamplesRespectStringSize() throws IOException {
        ManifestV1 manifest = new ManifestReader().read(string("""
                schemaVersion: v1
                project:
                  name: sizesample
                  groupId: dev.ainer.consumer
                  artifactId: sizesample
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 25
                database: postgresql
                entities:
                  - name: item
                    fields:
                      - name: code
                        type: string(8)
                """));
        String test = generate(manifest).files().stream()
                .filter(f -> f.path().endsWith("ItemCrudIntegrationTest.java"))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();

        assertThat(test).doesNotContain("\"code-created\"");
        assertThat(test).doesNotContain("\"code-updated\"");
        assertThat(test).contains("\"code-cre\"");
        assertThat(test).contains("\"code-upd\"");
    }

    @Test
    @DisplayName("entities 不允许与 database none 组合（fail-fast）")
    void entitiesWithNoDatabaseFailsFast() {
        assertThatThrownBy(() -> {
            ManifestV1 parsed = new ManifestReader().read(string("""
                    schemaVersion: v1
                    project:
                      name: bad
                      groupId: dev.ainer.consumer
                      artifactId: bad
                      version: 1.0.0
                    spring-boot: 4.1.0
                    ainner: 0.1.0
                    java: 25
                    database: none
                    entities:
                      - name: item
                        fields:
                          - name: label
                            type: string(16)
                    """));
            new ProjectGenerator(parsed).generate();
        })
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode())
                        .isEqualTo(InitializerErrorCode.INVALID_MANIFEST));
    }

    private String file(ProjectTree tree, String suffix) {
        return tree.files().stream()
                .filter(f -> f.path().endsWith(suffix))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
    }
    private long countBytes(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (var paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        }
    }

    private static java.io.Reader string(String yaml) {
        return new java.io.StringReader(yaml);
    }
}
