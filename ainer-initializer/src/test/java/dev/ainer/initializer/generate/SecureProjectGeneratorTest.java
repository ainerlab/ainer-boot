package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.manifest.ManifestReader;
import dev.ainer.initializer.manifest.ManifestV2;
import dev.ainer.initializer.manifest.ManifestV2Test;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureProjectGeneratorTest {

    @Test
    void v2GenerationIsDeterministicAndCarriesItsOwnWrapper() throws Exception {
        ManifestV2 manifest = manifest();
        ProjectTree first = new SecureProjectGenerator(manifest).generate();
        ProjectTree replay = new SecureProjectGenerator(manifest).generate();

        assertThat(first.files()).hasSameSizeAs(replay.files());
        for (int index = 0; index < first.files().size(); index++) {
            assertThat(first.files().get(index).path()).isEqualTo(replay.files().get(index).path());
            assertThat(first.files().get(index).bytes()).isEqualTo(replay.files().get(index).bytes());
        }
        assertThat(first.files()).anySatisfy(file -> {
            if (file.path().equals("mvnw")) {
                assertThat(file.executable()).isTrue();
            }
        });
    }

    @Test
    void v2EmitsSecureLayeredVerticalSlice() throws Exception {
        ProjectTree tree = new SecureProjectGenerator(manifest()).generate();

        assertThat(tree.files().stream().map(GeneratedFile::path)).contains(
                "src/main/java/dev/example/catalog/product/api/ProductApiDtos.java",
                "src/main/java/dev/example/catalog/product/application/ProductApplicationService.java",
                "src/main/java/dev/example/catalog/product/application/ProductAccessAuditService.java",
                "src/main/java/dev/example/catalog/product/infrastructure/ProductMapper.java",
                "src/test/java/dev/example/catalog/product/ProductSecureCrudIntegrationTest.java");

        String controller = file(tree, "ProductController.java");
        assertThat(controller)
                .contains("/api/workspaces/{workspaceId}/products", "@Valid", "AuthenticatedPrincipalResolver")
                .doesNotContain("ProductRow", "IPage", "BaseMapper");

        String service = file(tree, "ProductApplicationService.java");
        assertThat(service)
                .contains("workspaceService.get(principal, workspaceId)")
                .contains("principal.hasScope(requiredScope)")
                .contains("accessAudit.record")
                .contains("size > 100")
                .contains("updateByWorkspaceAndVersion")
                .doesNotContain("IPage", "QueryWrapper");

        String mapper = file(tree, "ProductMapper.java");
        assertThat(mapper)
                .contains("WHERE workspace_id = #{workspaceId}")
                .contains("AND version = #{expectedVersion}")
                .contains("LIMIT #{size} OFFSET #{offset}")
                .doesNotContain("${");

        String migration = file(tree, "V1__secure_catalog_service_product.sql");
        assertThat(migration)
                .contains("workspace_id uuid NOT NULL")
                .contains("version bigint NOT NULL DEFAULT 0")
                .contains("catalog_service_product_access_audit")
                .contains("DEFAULT uuidv7()");

        String pom = file(tree, "pom.xml");
        assertThat(pom).contains(
                "ainer-module-workspace",
                "ainer-module-authorization",
                "ainer-starter-security",
                "springdoc-openapi-starter-webmvc-ui");

        String application = file(tree, "Application.java");
        assertThat(application).contains(
                "AuthorizationModuleConfiguration.class",
                "WorkspaceModuleConfiguration.class");

        String test = file(tree, "ProductSecureCrudIntegrationTest.java");
        assertThat(test).contains(
                "AINER.COMMON.UNAUTHENTICATED",
                "AINER.WORKSPACE.NOT_FOUND",
                "CATALOG.PRODUCT.CONCURRENT_MODIFICATION",
                "size=101",
                "decision = 'DENY'");
    }

    @Test
    void v2GeneratesValidDistinctUuidFixtures() throws Exception {
        ManifestV2 manifest = parse(ManifestV2Test.validManifest()
                .replace("type: decimal", "type: uuid"));

        String test = file(new SecureProjectGenerator(manifest).generate(),
                "ProductSecureCrudIntegrationTest.java");

        assertThat(test)
                .contains("00000000-0000-0000-0000-000000000001")
                .contains("00000000-0000-0000-0000-000000000002");
    }

    @Test
    void additiveGenerationStartsAtExplicitMigrationAndOmitsProjectScaffold() throws Exception {
        ProjectTree tree = new SecureProjectGenerator(manifest()).generateAdditive(7);

        assertThat(tree.files().stream().map(GeneratedFile::path))
                .contains(
                        "src/main/resources/db/migration/V7__secure_catalog_service_product.sql",
                        "src/main/java/dev/example/catalog/initializer/"
                                + "AinerInitializerWorkspaceConfiguration.java",
                        "src/test/java/dev/example/catalog/support/SecureTestConfiguration.java")
                .doesNotContain("pom.xml", "mvnw", "README.md", "src/main/resources/application.yml");
        assertThat(file(tree, "AinerInitializerWorkspaceConfiguration.java"))
                .contains("@Import(WorkspaceModuleConfiguration.class)")
                .doesNotContain("@MapperScan");
    }

    @Test
    void additiveGenerationAcceptsMaximumMigrationVersionForOneEntity() throws Exception {
        ProjectTree tree = new SecureProjectGenerator(manifest()).generateAdditive(Long.MAX_VALUE);

        assertThat(tree.files().stream().map(GeneratedFile::path))
                .contains("src/main/resources/db/migration/V9223372036854775807"
                        + "__secure_catalog_service_product.sql");
    }

    @Test
    void v2RejectsBusinessFieldsThatCollideWithGeneratedColumns() throws Exception {
        ManifestV2 manifest = parse(ManifestV2Test.validManifest()
                .replace("name: price", "name: workspaceId"));

        assertThatThrownBy(() -> new SecureProjectGenerator(manifest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内建列重名: workspace_id");
    }

    @Test
    void v2RejectsPostgresColumnIdentifiersLongerThanLimit() throws Exception {
        String longField = "a".repeat(64);
        ManifestV2 manifest = parse(ManifestV2Test.validManifest()
                .replace("name: price", "name: " + longField));

        assertThatThrownBy(() -> new SecureProjectGenerator(manifest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PostgreSQL 标识符过长");
    }

    private ManifestV2 manifest() throws Exception {
        return parse(ManifestV2Test.validManifest());
    }

    private ManifestV2 parse(String yaml) throws Exception {
        return (ManifestV2) new ManifestReader().readProject(new StringReader(yaml));
    }

    private String file(ProjectTree tree, String suffix) {
        return tree.files().stream()
                .filter(file -> file.path().endsWith(suffix))
                .map(GeneratedFile::utf8)
                .findFirst()
                .orElseThrow();
    }
}
