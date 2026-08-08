package dev.ainer.initializer.preview;

import dev.ainer.initializer.generate.ProjectTree;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Read-only summary of what generation would produce. Renders text only; the CLI never writes
 * from a preview.
 */
public record ProjectPreview(ProjectTree tree, @Nullable Path displayRoot) {

    public static ProjectPreview of(ProjectTree tree) {
        return new ProjectPreview(tree, null);
    }

    public static ProjectPreview of(ProjectTree tree, Path displayRoot) {
        return new ProjectPreview(tree, displayRoot);
    }

    public String render() {
        StringBuilder builder = new StringBuilder();
        builder.append("将创建 ").append(tree.size()).append(" 个文件，共 ")
                .append(tree.totalBytes()).append(" 字节");
        if (displayRoot != null) {
            builder.append("，目标: ").append(displayRoot);
        }
        builder.append('\n');
        List<String> paths = tree.files().stream().map(f -> f.path()).sorted().toList();
        for (String path : paths) {
            builder.append("  ").append(path).append('\n');
        }
        return builder.toString();
    }
}