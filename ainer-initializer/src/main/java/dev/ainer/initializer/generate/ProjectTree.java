package dev.ainer.initializer.generate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic in-memory file tree produced by the generator. Map iteration order is the
 * generation order which the tests pin byte-for-byte.
 */
public final class ProjectTree {

    private final Map<String, GeneratedFile> files;

    public ProjectTree(List<GeneratedFile> files) {
        Objects.requireNonNull(files, "files");
        List<GeneratedFile> sorted = files.stream()
                .sorted(java.util.Comparator.comparing(GeneratedFile::path))
                .toList();
        Map<String, GeneratedFile> byPath = new LinkedHashMap<>();
        for (GeneratedFile file : sorted) {
            if (byPath.put(file.path(), file) != null) {
                throw new IllegalArgumentException("重复的生成路径: " + file.path());
            }
        }
        this.files = Collections.unmodifiableMap(byPath);
    }

    public List<GeneratedFile> files() {
        return List.copyOf(files.values());
    }

    public int size() {
        return files.size();
    }

    public long totalBytes() {
        return files.values().stream().mapToLong(f -> f.content().length).sum();
    }

    public boolean contains(String path) {
        return files.containsKey(path);
    }
}