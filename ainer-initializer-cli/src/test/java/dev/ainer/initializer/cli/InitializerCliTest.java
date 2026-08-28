package dev.ainer.initializer.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InitializerCliTest {

    @TempDir
    Path tempDir;

    private record RunResult(int exit, String stdout, String stderr) {
    }

    private RunResult run(String... args) throws IOException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            exit = new InitializerCli(out, err).run(args);
        }
        return new RunResult(exit, stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private Path writeSampleManifest() throws IOException {
        Path manifest = tempDir.resolve("manifest.yaml");
        Files.writeString(manifest, """
                schemaVersion: v1
                project:
                  name: Cli Sample
                  groupId: dev.ainer.consumer
                  artifactId: cli-sample
                  version: 1.0.0
                spring-boot: 4.1.1
                ainner: 0.1.0
                java: 25
                """);
        return manifest;
    }

    private Path writeV2Manifest() throws IOException {
        Path manifest = tempDir.resolve("manifest-v2.yaml");
        Files.writeString(manifest, """
                schemaVersion: v2
                preset: simple-service
                accessControl: workspace
                errorNamespace: CLI_SAMPLE
                project:
                  name: Secure CLI Sample
                  groupId: dev.ainer.consumer.secure
                  artifactId: secure-cli-sample
                  version: 1.0.0
                spring-boot: 4.1.1
                ainner: 1.0.0
                java: 25
                database: postgresql
                entities:
                  - name: note
                    fields:
                      - name: title
                        type: string(120)
                """);
        return manifest;
    }

    @Test
    @DisplayName("preview 只读输出文件清单")
    void previewShowsTree() throws IOException {
        Path manifest = writeSampleManifest();
        RunResult result = run("preview", manifest.toString());

        assertThat(result.exit()).isZero();
        assertThat(result.stdout()).contains("pom.xml", "PingController.java",
                "未写入任何文件");
        assertThat(tempDir.resolve("pom.xml")).doesNotExist();
    }

    @Test
    @DisplayName("init 生成到空目录")
    void initGeneratesProject() throws IOException {
        Path manifest = writeSampleManifest();
        Path target = tempDir.resolve("generated");
        Files.createDirectories(target);

        RunResult result = run("init", manifest.toString(), target.toString());

        assertThat(result.exit()).isZero();
        assertThat(target.resolve("pom.xml")).isRegularFile();
        assertThat(target.resolve("mvnw")).isRegularFile();
        assertThat(target.resolve("src/main/resources/application.yml")).isRegularFile();
    }

    @Test
    @DisplayName("CLI 识别 v2 并生成 Workspace 安全纵向切片")
    void initGeneratesSecureV2Project() throws IOException {
        Path manifest = writeV2Manifest();
        Path target = tempDir.resolve("generated-v2");

        RunResult result = run("init", manifest.toString(), target.toString());

        assertThat(result.exit()).isZero();
        assertThat(target.resolve(
                "src/main/java/dev/ainer/consumer/secure/note/application/NoteApplicationService.java"))
                .isRegularFile();
        assertThat(Files.readString(target.resolve("README.md")))
                .contains("manifest v2", "workspace_id");
    }

    @Test
    @DisplayName("init 拒绝非空目录")
    void initRefusesNonEmpty() throws IOException {
        Path manifest = writeSampleManifest();
        Path target = tempDir.resolve("occupied");
        Files.createDirectories(target);
        Files.writeString(target.resolve("keep.txt"), "keep");

        RunResult result = run("init", manifest.toString(), target.toString());

        assertThat(result.exit()).isEqualTo(3);
        assertThat(result.stderr()).contains("非空");
        assertThat(Files.readString(target.resolve("keep.txt"))).isEqualTo("keep");
    }

    @Test
    @DisplayName("diff 对生成后的目录无变更")
    void diffAfterInitIsClean() throws IOException {
        Path manifest = writeSampleManifest();
        Path target = tempDir.resolve("diffed");
        Files.createDirectories(target);
        run("init", manifest.toString(), target.toString());

        RunResult result = run("diff", manifest.toString(), target.toString());

        assertThat(result.exit()).isZero();
        assertThat(result.stdout()).contains("不变 10");
    }

    @Test
    @DisplayName("未知命令返回 2 并显示用法")
    void unknownCommandFails() throws IOException {
        RunResult result = run("frobnicate");
        assertThat(result.exit()).isEqualTo(2);
        assertThat(result.stderr()).contains("未知子命令");
    }

    @Test
    @DisplayName("plan-add 对已有项目只读规划 POM 与 V3 切片")
    void planAddIsReadOnly() throws IOException {
        Path manifest = writeV2Manifest();
        Path target = writeExistingProject("planned");
        String originalPom = Files.readString(target.resolve("pom.xml"));

        RunResult result = run(
                "plan-add", manifest.toString(), target.toString(), "--migration-version", "3");

        assertThat(result.exit()).isZero();
        assertThat(result.stdout()).contains(
                "Flyway 起始版本 V3", "POM 新增依赖", "未写入任何文件");
        assertThat(target.resolve(
                "src/main/resources/db/migration/V3__secure_secure_cli_sample_note.sql"))
                .doesNotExist();
        assertThat(Files.readString(target.resolve("pom.xml"))).isEqualTo(originalPom);
    }

    @Test
    @DisplayName("add 安全合并 POM 并幂等写入已有项目")
    void addIntegratesExistingProjectIdempotently() throws IOException {
        Path manifest = writeV2Manifest();
        Path target = writeExistingProject("integrated");

        RunResult first = run(
                "add", manifest.toString(), target.toString(), "--migration-version", "3");

        assertThat(first.exit()).isZero();
        assertThat(target.resolve(
                "src/main/resources/db/migration/V3__secure_secure_cli_sample_note.sql"))
                .isRegularFile();
        assertThat(target.resolve(
                "src/main/java/dev/ainer/consumer/secure/initializer/"
                        + "AinerInitializerWorkspaceConfiguration.java"))
                .isRegularFile();
        assertThat(Files.readString(target.resolve("pom.xml")))
                .contains(
                        "<maven.compiler.parameters>true</maven.compiler.parameters>",
                        "<artifactId>ainer-module-workspace</artifactId>",
                        "<artifactId>ainer-module-authorization</artifactId>",
                        "<artifactId>ainer-test-support</artifactId>")
                .containsOnlyOnce("<artifactId>ainer-starter-web</artifactId>");
        assertThat(Files.readString(target.resolve("keep.txt"))).isEqualTo("preserve");

        RunResult second = run(
                "add", manifest.toString(), target.toString(), "--migration-version", "3");

        assertThat(second.exit()).isZero();
        assertThat(second.stdout()).contains("新增文件 0", "POM 新增依赖 0");
    }

    @Test
    @DisplayName("add 在 Flyway 版本已占用时失败且不修改 POM")
    void addRefusesOccupiedMigrationVersion() throws IOException {
        Path manifest = writeV2Manifest();
        Path target = writeExistingProject("collision");
        Files.writeString(target.resolve(
                "src/main/resources/db/migration/V3__existing.sql"), "SELECT 1;\n");
        String originalPom = Files.readString(target.resolve("pom.xml"));

        RunResult result = run(
                "add", manifest.toString(), target.toString(), "--migration-version", "3");

        assertThat(result.exit()).isEqualTo(3);
        assertThat(result.stderr()).contains("migration 版本 V3 已被占用");
        assertThat(Files.readString(target.resolve("pom.xml"))).isEqualTo(originalPom);
        assertThat(target.resolve(
                "src/main/java/dev/ainer/consumer/secure/note/application/NoteApplicationService.java"))
                .doesNotExist();
    }

    private Path writeExistingProject(String name) throws IOException {
        Path target = tempDir.resolve(name);
        Path source = target.resolve("src/main/java/dev/ainer/consumer/secure");
        Path migrations = target.resolve("src/main/resources/db/migration");
        Files.createDirectories(source);
        Files.createDirectories(migrations);
        Files.writeString(source.resolve("ExistingApplication.java"), """
                package dev.ainer.consumer.secure;

                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class ExistingApplication {
                }
                """);
        Files.writeString(migrations.resolve("V1__base.sql"), "SELECT 1;\n");
        Files.writeString(migrations.resolve("V2__existing.sql"), "SELECT 2;\n");
        Files.writeString(target.resolve("keep.txt"), "preserve");
        Files.writeString(target.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>dev.ainer.consumer.secure</groupId>
                    <artifactId>existing-consumer</artifactId>
                    <version>1.0.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>dev.ainer</groupId>
                                <artifactId>ainer-dependencies</artifactId>
                                <version>1.0.0</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>dev.ainer</groupId>
                            <artifactId>ainer-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);
        return target;
    }
}
