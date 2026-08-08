package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A single generated file held in memory. Paths are archive-relative (POSIX separators),
 * content is a fixed byte array so repeated generation is byte-identical.
 */
public record GeneratedFile(String relativePath, byte[] content) {

    public GeneratedFile {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
        if (relativePath.startsWith("/") || relativePath.contains("..")) {
            throw new BusinessException(InitializerErrorCode.ILLEGAL_STATE,
                    "非法生成路径: " + relativePath);
        }
    }

    public String path() {
        return relativePath;
    }

    public byte[] bytes() {
        return content;
    }

    public String utf8() {
        return new String(content, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Resolves this file into {@code targetDir} without touching anything else. */
    public void writeTo(Path targetDir) {
        try {
            Path resolved = targetDir.resolve(relativePath);
            Files.createDirectories(resolved.getParent());
            Files.write(resolved, content);
        } catch (java.io.IOException e) {
            throw new BusinessException(InitializerErrorCode.ILLEGAL_STATE,
                    "写入 " + relativePath + " 失败");
        }
    }
}