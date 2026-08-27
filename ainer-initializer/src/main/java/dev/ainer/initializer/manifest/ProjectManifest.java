package dev.ainer.initializer.manifest;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * Initializer CLI 使用的版本无关输入。
 *
 * <p>各版本合同仍是不可变 record；本接口只暴露确定性生成器共享的项目字段，不表示 v1 与 v2
 * 模板可以互换。
 */
public interface ProjectManifest {

    String schemaVersion();

    ProjectInfo project();

    int javaRelease();

    String springBootVersion();

    String ainerVersion();

    @Nullable String packageName();

    List<String> starters();

    ManifestV1.Database database();

    List<EntityDeclaration> entities();

    @Nullable Owner owner();

    default String resolvedPackageName() {
        return packageName() == null ? project().groupId() : packageName();
    }

    default String packagePath() {
        return resolvedPackageName().replace('.', '/');
    }

    default List<String> effectiveStarters() {
        return Stream.concat(ManifestV1.BASE_STARTERS.stream(), starters().stream())
                .distinct()
                .toList();
    }
}
