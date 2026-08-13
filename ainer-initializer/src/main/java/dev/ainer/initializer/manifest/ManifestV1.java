package dev.ainer.initializer.manifest;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Manifest v1 — the only input contract of the Ainer Project Initializer (ADR-0035).
 *
 * <p>Values are immutable and fully validated before generation starts. Unknown fields,
 * unknown starters and template look-alike values fail fast instead of being silently ignored.
 */
public record ManifestV1(
        ProjectInfo project,
        int javaRelease,
        String springBootVersion,
        String ainerVersion,
        @Nullable String packageName,
        List<String> starters,
        Database database,
        List<EntityDeclaration> entities,
        @Nullable Owner owner) {

    /** Schema marker required by the generator. */
    public static final String SCHEMA_VERSION = "v1";

    /** Framework starter always part of a generated project. */
    public static final String FRAMEWORK_STARTER_WEB = "dev.ainer:ainer-starter-web";

    /** Starters implied for every project regardless of the manifest. */
    public static final List<String> BASE_STARTERS = List.of(FRAMEWORK_STARTER_WEB);

    public enum Database {
        NONE,
        POSTGRESQL
    }

    public ManifestV1 {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(springBootVersion, "springBootVersion");
        Objects.requireNonNull(ainerVersion, "ainerVersion");
        if (javaRelease != 25) {
            fail("java.release 仅支持 25，收到: " + javaRelease);
        }
        starters = List.copyOf(Objects.requireNonNull(starters, "starters"));
        validateStarters(starters);
        database = database == null ? Database.NONE : database;
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        if (!entities.isEmpty() && database != Database.POSTGRESQL) {
            fail("entities 只允许在 database 为 postgresql 时声明（CRUD 生成需要真实表）");
        }
        if (packageName != null && packageName.isBlank()) {
            fail("package.name 不能为空字符串");
        }
    }

    private static void validateStarters(List<String> starters) {
        for (String starter : starters) {
            if (starter == null || starter.isBlank()) {
                fail("starters 元素不能为空");
            }
            if (!starter.contains(":")) {
                fail("starter 必须是 groupId:artifactId 格式，收到: " + starter);
            }
            if (starter.contains("{{") || starter.contains("}}")) {
                fail("starter 不能包含模板占位符");
            }
        }
    }

    /** All starters a generated project receives; the web starter is always implied. */
    public List<String> effectiveStarters() {
        return Stream.concat(BASE_STARTERS.stream(), starters.stream()).distinct().toList();
    }

    /**
     * Root package used for generated sources. Explicit {@code package.name} wins; otherwise
     * derived from the groupId (e.g. {@code dev.ainer.consumer}).
     */
    public String resolvedPackageName() {
        if (packageName != null) {
            return packageName;
        }
        return project.groupId();
    }

    /** Package path with dots replaced by slashes, e.g. {@code dev/ainer/consumer}. */
    public String packagePath() {
        return resolvedPackageName().replace('.', '/');
    }

    private static void fail(String message) {
        throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST, message);
    }
}