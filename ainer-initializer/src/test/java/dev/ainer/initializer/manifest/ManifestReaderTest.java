package dev.ainer.initializer.manifest;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestReaderTest {

    private final ManifestReader reader = new ManifestReader();

    @Test
    @DisplayName("合法 manifest 解析并派生包名")
    void readsValidManifest() throws IOException {
        ManifestV1 manifest = reader.read(string(
                """
                schemaVersion: v1
                project:
                  name: Ainer Consumer Sample
                  groupId: dev.ainer.consumer
                  artifactId: sample-project
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 25
                """));

        assertThat(manifest.project().name()).isEqualTo("Ainer Consumer Sample");
        assertThat(manifest.project().artifactId()).isEqualTo("sample-project");
        assertThat(manifest.resolvedPackageName()).isEqualTo("dev.ainer.consumer");
        assertThat(manifest.database()).isEqualTo(ManifestV1.Database.NONE);
        assertThat(manifest.effectiveStarters()).containsExactly(
                ManifestV1.FRAMEWORK_STARTER_WEB);
    }

    @Test
    @DisplayName("schemaVersion 必须是 v1")
    void rejectsWrongSchemaVersion() {
        assertInvalid("""
                schemaVersion: v2
                project:
                  name: x
                  groupId: dev.ainer
                  artifactId: x
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 25
                """);
    }

    @Test
    @DisplayName("未知字段 fail-fast")
    void rejectsUnknownField() {
        assertInvalid("""
                schemaVersion: v1
                project:
                  name: x
                  groupId: dev.ainer
                  artifactId: x
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 25
                mystery: true
                """);
    }

    @Test
    @DisplayName("SNAPSHOT ainerVersion 必须显式 allowSnapshot")
    void rejectsSnapshotWithoutAllowance() {
        assertInvalid("""
                schemaVersion: v1
                project:
                  name: x
                  groupId: dev.ainer
                  artifactId: x
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0-SNAPSHOT
                java: 25
                """);
    }

    @Test
    @DisplayName("模板占位符被拒绝")
    void rejectsTemplateLiteral() {
        assertInvalid("""
                schemaVersion: v1
                project:
                  name: x
                  groupId: dev.ainer
                  artifactId: x
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: {{version}}
                java: 25
                """);
    }

    @Test
    @DisplayName("javax 25 与 spring-boot 版本非法时失败")
    void rejectsUnsupportedRuntime() {
        assertInvalid("""
                schemaVersion: v1
                project:
                  name: x
                  groupId: dev.ainer
                  artifactId: x
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 21
                """);
    }

    @Test
    @DisplayName("owner 邮箱非法时失败")
    void rejectsInvalidOwnerEmail() {
        assertInvalid("""
                schemaVersion: v1
                project:
                  name: x
                  groupId: dev.ainer
                  artifactId: x
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 25
                owner:
                  email: not-an-email
                """);
    }

    @Test
    @DisplayName("database postgresql 与额外 starter 被接受")
    void acceptsPostgresAndExtraStarters() throws IOException {
        ManifestV1 manifest = reader.read(string(
                """
                schemaVersion: v1
                project:
                  name: pg-sample
                  groupId: dev.ainer.consumer
                  artifactId: pg-sample
                  version: 1.0.0
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 25
                package: dev.ainer.consumer.pg
                database: postgresql
                starters:
                  - dev.ainer:ainer-starter-persistence
                owner:
                  displayName: Ainer Team
                  email: team@example.com
                """));

        assertThat(manifest.database()).isEqualTo(ManifestV1.Database.POSTGRESQL);
        assertThat(manifest.effectiveStarters()).contains(
                "dev.ainer:ainer-starter-persistence");
        assertThat(manifest.owner()).isNotNull();
        assertThat(manifest.owner().email()).isEqualTo("team@example.com");
        assertThat(manifest.resolvedPackageName()).isEqualTo("dev.ainer.consumer.pg");
    }

    private void assertInvalid(String yaml) {
        assertThatThrownBy(() -> reader.read(string(yaml)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode())
                        .isEqualTo(InitializerErrorCode.INVALID_MANIFEST));
    }

    private static Reader string(String yaml) {
        return new java.io.StringReader(yaml);
    }
}