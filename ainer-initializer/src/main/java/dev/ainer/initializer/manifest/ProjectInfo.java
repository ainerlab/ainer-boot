package dev.ainer.initializer.manifest;

import org.jspecify.annotations.Nullable;

/**
 * Project identity section of Manifest v1.
 *
 * @param name        human readable name, 1–120 characters
 * @param groupId     Maven groupId (dot-separated segments seeded with letter/underscore)
 * @param artifactId  Maven artifactId used for the POM and artefact names
 * @param version     semantic version, pinned, non-SNAPSHOT unless {@code allowSnapshot}
 * @param description optional description shown in the generated README
 */
public record ProjectInfo(
        String name,
        String groupId,
        String artifactId,
        String version,
        @Nullable String description) {

    public ProjectInfo {
        requireNonBlank(name, "project.name");
        requireNonBlank(groupId, "project.groupId");
        requireNonBlank(artifactId, "project.artifactId");
        requireNonBlank(version, "project.version");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}