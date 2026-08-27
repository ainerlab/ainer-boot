package dev.ainer.initializer.manifest;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manifest v2 安全纵向切片合同（ADR-0052）。
 *
 * <p>首个 v2 预设有意保持狭窄：只生成 PostgreSQL 简单服务，并让所有资源都经过可信 Workspace
 * membership 校验。新增拓扑或访问模型必须增加枚举值，并提供独立模板与消费者门禁。
 */
public record ManifestV2(
        ProjectInfo project,
        int javaRelease,
        String springBootVersion,
        String ainerVersion,
        @Nullable String packageName,
        List<String> starters,
        ManifestV1.Database database,
        List<EntityDeclaration> entities,
        @Nullable Owner owner,
        Preset preset,
        AccessControl accessControl,
        String errorNamespace) implements ProjectManifest {

    public static final String SCHEMA_VERSION = "v2";
    private static final Set<String> SUPPORTED_STARTERS = Set.of(
            ManifestV1.FRAMEWORK_STARTER_WEB,
            "dev.ainer:ainer-starter-persistence",
            "dev.ainer:ainer-starter-security",
            "dev.ainer:ainer-starter-observability",
            "dev.ainer:ainer-starter-cache");

    public enum Preset {
        SIMPLE_SERVICE
    }

    public enum AccessControl {
        WORKSPACE
    }

    public ManifestV2 {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(springBootVersion, "springBootVersion");
        Objects.requireNonNull(ainerVersion, "ainerVersion");
        if (javaRelease != 25) {
            fail("java.release 仅支持 25，收到: " + javaRelease);
        }
        starters = List.copyOf(Objects.requireNonNull(starters, "starters"));
        database = Objects.requireNonNull(database, "database");
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        preset = Objects.requireNonNull(preset, "preset");
        accessControl = Objects.requireNonNull(accessControl, "accessControl");
        Objects.requireNonNull(errorNamespace, "errorNamespace");
        if (database != ManifestV1.Database.POSTGRESQL) {
            fail("Manifest v2 simple-service 必须使用 database: postgresql");
        }
        if (entities.isEmpty()) {
            fail("Manifest v2 simple-service 至少需要一个 entities 声明");
        }
        Set<String> entityClasses = new HashSet<>();
        for (EntityDeclaration entity : entities) {
            if (!entityClasses.add(entity.className())) {
                fail("多个实体生成了同一个 Java 类名前缀: " + entity.className());
            }
            for (EntityField field : entity.fields()) {
                if (field.initial() != null) {
                    fail("Manifest v2 暂不支持 fields.initial: " + entity.name() + "." + field.name());
                }
            }
        }
        for (String starter : starters) {
            if (!SUPPORTED_STARTERS.contains(starter)) {
                fail("Manifest v2 不支持的额外 starter: " + starter);
            }
        }
        if (!errorNamespace.matches("[A-Z][A-Z0-9_]{1,31}")) {
            fail("errorNamespace 必须匹配 [A-Z][A-Z0-9_]{1,31}，收到: " + errorNamespace);
        }
        if (packageName != null && packageName.isBlank()) {
            fail("package.name 不能为空字符串");
        }
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }

    private static void fail(String message) {
        throw new BusinessException(InitializerErrorCode.INVALID_MANIFEST, message);
    }
}
