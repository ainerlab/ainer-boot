package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Persists a generated tree into an empty (or non-existent) target directory. Refuses to
 * overwrite or delete anything: an existing non-empty target is a hard error unless the
 * caller explicitly passes {@code force}; even then foreign files are never deleted.
 */
public final class ProjectWriter {

    /** 已有项目增量写入结果。 */
    public record AdditiveWriteResult(List<String> newFiles, List<String> unchangedFiles) {
        public AdditiveWriteResult {
            newFiles = List.copyOf(newFiles);
            unchangedFiles = List.copyOf(unchangedFiles);
        }
    }

    /** 已有项目只新增计划。 */
    public record AdditivePlan(
            List<String> newFiles,
            List<String> unchangedFiles,
            List<String> conflictingFiles) {
        public AdditivePlan {
            newFiles = List.copyOf(newFiles);
            unchangedFiles = List.copyOf(unchangedFiles);
            conflictingFiles = List.copyOf(conflictingFiles);
        }

        public boolean hasConflicts() {
            return !conflictingFiles.isEmpty();
        }
    }

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

    /**
     * 向已有目录只写入新文件；相同字节与执行位的路径视为幂等，不同内容或类型一律拒绝。
     * 本方法不覆盖、不删除，也不接受 {@code force}。
     */
    public AdditiveWriteResult writeAdditive(ProjectTree tree, Path target) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(target, "target");
        if (!Files.isDirectory(target)) {
            throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                    "已有项目目标必须是目录: " + target);
        }

        AdditivePlan plan = inspectAdditive(tree, target);
        if (plan.hasConflicts()) {
            throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                    "已有项目存在不同内容的目标路径，拒绝覆盖: " + plan.conflictingFiles());
        }
        List<String> written = new ArrayList<>();
        try {
            for (GeneratedFile file : tree.files()) {
                if (plan.newFiles().contains(file.path())) {
                    written.add(file.path());
                    file.writeNewTo(target);
                }
            }
        } catch (RuntimeException exception) {
            rollbackAdditive(tree, target, written);
            throw exception;
        }
        return new AdditiveWriteResult(plan.newFiles(), plan.unchangedFiles());
    }

    /**
     * 回滚本轮确实写入且仍与生成结果完全相同的新文件。若用户并发修改了文件则保留，绝不误删。
     */
    public void rollbackAdditive(ProjectTree tree, Path target, List<String> writtenFiles) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(writtenFiles, "writtenFiles");
        for (String relative : writtenFiles.reversed()) {
            GeneratedFile generated = tree.files().stream()
                    .filter(file -> file.path().equals(relative))
                    .findFirst()
                    .orElse(null);
            if (generated == null) {
                continue;
            }
            Path path = target.resolve(relative);
            try {
                if (Files.isRegularFile(path) && sameFile(path, generated)) {
                    Files.delete(path);
                    deleteEmptyParents(path.getParent(), target);
                }
            } catch (IOException ignored) {
                // Best-effort cleanup only; never replace the original failure or delete changed user data.
            }
        }
    }

    /** 只读检查已有项目增量路径，不把外部文件误报为删除建议。 */
    public AdditivePlan inspectAdditive(ProjectTree tree, Path target) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(target, "target");
        if (!Files.isDirectory(target)) {
            throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                    "已有项目目标必须是目录: " + target);
        }
        List<String> newFiles = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (GeneratedFile file : tree.files()) {
            Path resolved = target.resolve(file.path());
            if (!Files.exists(resolved)) {
                newFiles.add(file.path());
            } else if (Files.isRegularFile(resolved) && sameFile(resolved, file)) {
                unchanged.add(file.path());
            } else {
                conflicts.add(file.path());
            }
        }
        return new AdditivePlan(newFiles, unchanged, conflicts);
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

    private boolean sameFile(Path path, GeneratedFile generated) {
        try {
            if (!java.util.Arrays.equals(Files.readAllBytes(path), generated.content())) {
                return false;
            }
            if (Files.getFileAttributeView(path, PosixFileAttributeView.class) == null) {
                return true;
            }
            var permissions = Files.getPosixFilePermissions(path);
            boolean executable = permissions.contains(PosixFilePermission.OWNER_EXECUTE)
                    || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                    || permissions.contains(PosixFilePermission.OTHERS_EXECUTE);
            return executable == generated.executable();
        } catch (IOException exception) {
            return false;
        }
    }

    private void deleteEmptyParents(Path directory, Path target) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path current = directory.toAbsolutePath().normalize();
        while (!current.equals(normalizedTarget) && current.startsWith(normalizedTarget)) {
            try {
                Files.delete(current);
            } catch (java.nio.file.DirectoryNotEmptyException notEmpty) {
                return;
            }
            current = current.getParent();
        }
    }
}
