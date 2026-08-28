package dev.ainer.initializer.integrate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import dev.ainer.initializer.generate.GeneratedFile;
import dev.ainer.initializer.generate.ProjectTree;
import dev.ainer.initializer.generate.ProjectWriter;
import dev.ainer.initializer.generate.SecureProjectGenerator;
import dev.ainer.initializer.manifest.ManifestV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 Manifest v2 安全切片增量接入已有单模块 Maven/Spring Boot 项目。
 *
 * <p>集成器只做三类受控变化：新增生成文件、同字节文件幂等保留、向顶层 POM 添加缺失依赖和
 * compiler parameter。任何已有源码、migration 或配置内容冲突都会在写盘前失败。
 */
public final class ExistingProjectIntegrator {

    private static final Pattern MIGRATION = Pattern.compile("^V([0-9]+)__.+\\.sql$");
    private static final Pattern PACKAGE = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;");

    private final ManifestV2 manifest;
    private final SecureProjectGenerator generator;
    private final ProjectWriter writer = new ProjectWriter();
    private final MavenPomEditor pomEditor = new MavenPomEditor();

    public ExistingProjectIntegrator(ManifestV2 manifest) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.generator = new SecureProjectGenerator(manifest);
    }

    /** 完整只读计划。 */
    public record Plan(
            Path target,
            long migrationVersion,
            ProjectTree tree,
            ProjectWriter.AdditivePlan files,
            MavenPomEditor.Patch pom) {
    }

    /** 写入结果。 */
    public record Result(Plan plan, ProjectWriter.AdditiveWriteResult files) {
    }

    public Plan plan(Path target, long migrationVersion) {
        Objects.requireNonNull(target, "target");
        Path absoluteTarget = target.toAbsolutePath().normalize();
        if (!Files.isDirectory(absoluteTarget)) {
            fail("已有项目目标必须是目录: " + absoluteTarget);
        }
        verifySpringBootApplication(absoluteTarget);
        ProjectTree tree = generator.generateAdditive(migrationVersion);
        ProjectWriter.AdditivePlan filePlan = writer.inspectAdditive(tree, absoluteTarget);
        if (filePlan.hasConflicts()) {
            fail("已有项目存在不同内容的目标路径，拒绝覆盖: " + filePlan.conflictingFiles());
        }
        verifyMigrationVersions(absoluteTarget, tree, filePlan);
        MavenPomEditor.Patch pomPatch = pomEditor.plan(
                absoluteTarget.resolve("pom.xml"), manifest.ainerVersion());
        return new Plan(absoluteTarget, migrationVersion, tree, filePlan, pomPatch);
    }

    public Result apply(Path target, long migrationVersion) {
        Plan plan = plan(target, migrationVersion);
        ProjectWriter.AdditiveWriteResult fileResult = writer.writeAdditive(plan.tree(), plan.target());
        try {
            pomEditor.apply(plan.pom());
        } catch (RuntimeException exception) {
            writer.rollbackAdditive(plan.tree(), plan.target(), fileResult.newFiles());
            throw exception;
        }
        return new Result(plan, fileResult);
    }

    private void verifySpringBootApplication(Path target) {
        Path sourceRoot = target.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            fail("已有项目缺少 src/main/java");
        }
        List<String> applicationPackages = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path javaFile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(javaFile, StandardCharsets.UTF_8);
                if (!source.contains("@SpringBootApplication")) {
                    continue;
                }
                Matcher matcher = PACKAGE.matcher(source);
                if (matcher.find()) {
                    applicationPackages.add(matcher.group(1));
                }
            }
        } catch (IOException exception) {
            fail("无法读取已有项目 Java 源码: " + safeMessage(exception));
        }
        boolean scanned = applicationPackages.stream().anyMatch(applicationPackage ->
                manifest.resolvedPackageName().equals(applicationPackage)
                        || manifest.resolvedPackageName().startsWith(applicationPackage + "."));
        if (!scanned) {
            fail("manifest package 不在任何 @SpringBootApplication 的默认扫描范围内："
                    + manifest.resolvedPackageName() + "，应用包=" + applicationPackages);
        }
    }

    private void verifyMigrationVersions(
            Path target,
            ProjectTree tree,
            ProjectWriter.AdditivePlan filePlan) {
        Path migrationRoot = target.resolve("src/main/resources/db/migration");
        Map<String, List<String>> existingByVersion = new HashMap<>();
        if (Files.isDirectory(migrationRoot)) {
            try (var paths = Files.list(migrationRoot)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    Matcher matcher = MIGRATION.matcher(path.getFileName().toString());
                    if (matcher.matches()) {
                        existingByVersion.computeIfAbsent(matcher.group(1), ignored -> new ArrayList<>())
                                .add(target.relativize(path).toString());
                    }
                });
            } catch (IOException exception) {
                fail("无法读取已有 Flyway migration: " + safeMessage(exception));
            }
        }
        for (GeneratedFile generated : tree.files()) {
            Path generatedPath = Path.of(generated.path());
            if (!generatedPath.startsWith("src/main/resources/db/migration")) {
                continue;
            }
            Matcher matcher = MIGRATION.matcher(generatedPath.getFileName().toString());
            if (!matcher.matches()) {
                fail("生成结果包含非法 Flyway migration 名称: " + generated.path());
            }
            List<String> existing = existingByVersion.getOrDefault(matcher.group(1), List.of());
            boolean exactIdempotent = existing.size() == 1
                    && existing.getFirst().equals(generated.path())
                    && filePlan.unchangedFiles().contains(generated.path());
            if (!existing.isEmpty() && !exactIdempotent) {
                fail("Flyway migration 版本 V" + matcher.group(1)
                        + " 已被占用: " + existing + "；请显式选择新的 --migration-version");
            }
        }
    }

    private String safeMessage(Throwable exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void fail(String message) {
        throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET, message);
    }
}
