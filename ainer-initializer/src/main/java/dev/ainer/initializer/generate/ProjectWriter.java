package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Persists a generated tree into an empty (or non-existent) target directory. Refuses to
 * overwrite or delete anything: an existing non-empty target is a hard error unless the
 * caller explicitly passes {@code force}; even then foreign files are never deleted.
 */
public final class ProjectWriter {

    /** Writes every file. Without {@code force} the target must not exist or must be empty. */
    public void write(ProjectTree tree, Path target, boolean force) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(target, "target");
        if (Files.exists(target)) {
            if (!Files.isDirectory(target)) {
                throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                        "目标已存在但不是目录: " + target);
            }
            List<String> existing = listFiles(target);
            if (!existing.isEmpty() && !force) {
                throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                        "目标目录非空，拒绝覆盖（已有 " + existing.size() + " 个文件，例如 "
                                + existing.get(0) + "）；使用 --force 需显式确认");
            }
        } else {
            try {
                Files.createDirectories(target);
            } catch (IOException e) {
                throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                        "无法创建目标目录: " + target);
            }
        }
        if (!force) {
            resolveConflicts(tree, target);
        }
        for (GeneratedFile file : tree.files()) {
            file.writeTo(target);
        }
    }

    /**
     * Reports every target path that would collide with an existing file. The caller decides
     * whether an overwrite is really intended; the writer never deletes foreign files.
     */
    public List<String> existingTargetPaths(ProjectTree tree, Path target) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(target, "target");
        List<String> collisions = new ArrayList<>();
        for (GeneratedFile file : tree.files()) {
            Path resolved = target.resolve(file.path());
            if (Files.exists(resolved) && Files.isRegularFile(resolved)) {
                collisions.add(file.path());
            }
        }
        return List.copyOf(collisions);
    }

    private void resolveConflicts(ProjectTree tree, Path target) {
        List<String> conflicts = existingTargetPaths(tree, target);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                    "目标目录已有与生成结果冲突的文件: " + conflicts);
        }
    }

    private List<String> listFiles(Path target) {
        try (var paths = Files.walk(target)) {
            return paths.filter(Files::isRegularFile)
                    .map(target::relativize)
                    .map(path -> path.toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                    "无法读取目标目录: " + target);
        }
    }
}