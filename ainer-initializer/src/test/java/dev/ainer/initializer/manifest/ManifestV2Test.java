package dev.ainer.initializer.manifest;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ManifestV2Test {

    private final ManifestReader reader = new ManifestReader();

    @Test
    void readsSecureSimpleServiceContractWithoutChangingV1Reader() throws IOException {
        ProjectManifest parsed = reader.readProject(new StringReader(validManifest()));

        assertThat(parsed).isInstanceOf(ManifestV2.class);
        ManifestV2 manifest = (ManifestV2) parsed;
        assertThat(manifest.schemaVersion()).isEqualTo("v2");
        assertThat(manifest.preset()).isEqualTo(ManifestV2.Preset.SIMPLE_SERVICE);
        assertThat(manifest.accessControl()).isEqualTo(ManifestV2.AccessControl.WORKSPACE);
        assertThat(manifest.errorNamespace()).isEqualTo("CATALOG");
        assertThat(manifest.entities()).hasSize(1);

        assertThatThrownBy(() -> reader.read(new StringReader(validManifest())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("v1 不允许未知字段");
    }

    @Test
    void v2RequiresPostgresqlEntitiesAndExplicitWorkspaceAccess() {
        assertInvalid(validManifest().replace("database: postgresql", "database: none"),
                "database: postgresql");
        assertInvalid(validManifest().replace("accessControl: workspace", "accessControl: owner"),
                "accessControl 必须是 workspace");
        assertInvalid(validManifest().replace("errorNamespace: CATALOG", "errorNamespace: catalog"),
                "errorNamespace");
        assertInvalid(validManifest().replace("comment: Product name",
                        "comment: Product name\n        initial: now()"),
                "暂不支持 fields.initial");
        assertInvalid(validManifest().replace("database: postgresql",
                        "database: postgresql\nstarters:\n  - dev.example:unsupported"),
                "不支持的额外 starter");
    }

    private void assertInvalid(String yaml, String message) {
        assertThatThrownBy(() -> reader.readProject(new StringReader(yaml)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(message)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(InitializerErrorCode.INVALID_MANIFEST));
    }

    public static String validManifest() {
        return """
                schemaVersion: v2
                preset: simple-service
                accessControl: workspace
                errorNamespace: CATALOG
                project:
                  name: Secure Catalog
                  groupId: dev.example.catalog
                  artifactId: catalog-service
                  version: 1.0.0
                spring-boot: 4.1.1
                ainner: 1.0.0
                java: 25
                database: postgresql
                entities:
                  - name: product
                    fields:
                      - name: name
                        type: string(120)
                        comment: Product name
                      - name: price
                        type: decimal
                        nullable: true
                """;
    }
}
