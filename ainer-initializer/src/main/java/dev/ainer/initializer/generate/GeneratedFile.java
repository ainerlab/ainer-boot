package dev.ainer.initializer.generate;

import dev.ainer.core.error.BusinessException;
import dev.ainer.initializer.error.InitializerErrorCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A single generated file held in memory. Paths are archive-relative (POSIX separators),
 * content is a fixed byte array so repeated generation is byte-identical.
 */
public record GeneratedFile(String relativePath, byte[] content, boolean executable) {

    private static final Set<PosixFilePermission> REGULAR_FILE_PERMISSIONS = Set.copyOf(EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ));

    private static final Set<PosixFilePermission> EXECUTABLE_FILE_PERMISSIONS = Set.copyOf(EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE));

    public GeneratedFile(String relativePath, byte[] content) {
        this(relativePath, content, false);
    }

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
            applyPermissions(resolved);
        } catch (java.io.IOException e) {
            throw new BusinessException(InitializerErrorCode.ILLEGAL_STATE,
                    "写入 " + relativePath + " 失败");
        }
    }

    /** Writes a new path only; never replaces a file created after an additive plan. */
    public void writeNewTo(Path targetDir) {
        try {
            Path resolved = targetDir.resolve(relativePath);
            Files.createDirectories(resolved.getParent());
            Files.write(resolved, content, StandardOpenOption.CREATE_NEW);
            applyPermissions(resolved);
        } catch (java.io.IOException e) {
            throw new BusinessException(InitializerErrorCode.ILLEGAL_STATE,
                    "只新增写入 " + relativePath + " 失败");
        }
    }

    private void applyPermissions(Path resolved) throws java.io.IOException {
        if (Files.getFileAttributeView(resolved, PosixFileAttributeView.class) == null) {
            return;
        }
        Files.setPosixFilePermissions(resolved,
                executable ? EXECUTABLE_FILE_PERMISSIONS : REGULAR_FILE_PERMISSIONS);
    }
}
