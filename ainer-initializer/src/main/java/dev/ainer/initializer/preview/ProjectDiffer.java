package dev.ainer.initializer.preview;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;
import dev.ainer.initializer.generate.GeneratedFile;
import dev.ainer.initializer.generate.ProjectTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Read-only comparison between the generated tree and an existing target directory.
 * Never writes, never deletes; deletion candidates are only reported.
 */
public final class ProjectDiffer {

    /** Result of a read-only inspection. */
    public record DiffResult(
            List<String> newFiles,
            List<String> modifiedFiles,
            List<String> unchangedFiles,
            List<String> suggestedDeletes) {

        public DiffResult {
            newFiles = List.copyOf(newFiles);
            modifiedFiles = List.copyOf(modifiedFiles);
            unchangedFiles = List.copyOf(unchangedFiles);
            suggestedDeletes = List.copyOf(suggestedDeletes);
        }

        public boolean hasChanges() {
            return !newFiles.isEmpty() || !modifiedFiles.isEmpty() || !suggestedDeletes.isEmpty();
        }

        public int changedCount() {
            return newFiles.size() + modifiedFiles.size();
        }
    }

    /** Computes the diff; target may be missing (everything is new). */
    public DiffResult diff(ProjectTree tree, Path target) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(target, "target");
        if (!Files.exists(target)) {
            List<String> all = tree.files().stream().map(GeneratedFile::path).toList();
            return new DiffResult(all, List.of(), List.of(), List.of());
        }
        if (!Files.isDirectory(target)) {
            throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                    "目标已存在但不是目录: " + target);
        }

        List<String> newFiles = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        for (GeneratedFile file : tree.files()) {
            Path resolved = target.resolve(file.path());
            if (!Files.exists(resolved)) {
                newFiles.add(file.path());
            } else if (Files.isRegularFile(resolved) && sameBytes(resolved, file.content())) {
                unchanged.add(file.path());
            } else {
                modified.add(file.path());
            }
        }
        return new DiffResult(sort(newFiles), sort(modified), sort(unchanged),
                sort(suggestedDeletes(tree, target)));
    }

    private List<String> suggestedDeletes(ProjectTree tree, Path target) {
        List<String> existing = new ArrayList<>();
        try (var paths = Files.walk(target)) {
            paths.filter(Files::isRegularFile)
                    .map(target::relativize)
                    .map(Path::toString)
                    .forEach(existing::add);
        } catch (IOException e) {
            throw new BusinessException(InitializerErrorCode.UNSUPPORTED_TARGET,
                    "无法读取目标目录: " + target);
        }
        return existing.stream().filter(path -> !tree.contains(path)).toList();
    }

    private boolean sameBytes(Path file, byte[] content) {
        try {
            byte[] onDisk = Files.readAllBytes(file);
            return java.util.Arrays.equals(onDisk, content);
        } catch (IOException e) {
            return false;
        }
    }

    private List<String> sort(List<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }
}