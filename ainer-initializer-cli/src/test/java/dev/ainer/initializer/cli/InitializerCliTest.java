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
                spring-boot: 4.1.0
                ainner: 0.1.0
                java: 25
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
        assertThat(target.resolve("src/main/resources/application.yml")).isRegularFile();
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
        assertThat(result.stdout()).contains("不变 7");
    }

    @Test
    @DisplayName("未知命令返回 2 并显示用法")
    void unknownCommandFails() throws IOException {
        RunResult result = run("frobnicate");
        assertThat(result.exit()).isEqualTo(2);
        assertThat(result.stderr()).contains("未知子命令");
    }
}