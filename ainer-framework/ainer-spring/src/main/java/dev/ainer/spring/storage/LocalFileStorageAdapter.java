package dev.ainer.spring.storage;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.storage.FileStoragePort;
import dev.ainer.core.storage.StorageErrorCode;
import dev.ainer.core.storage.StoredFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 本地文件系统 {@link FileStoragePort} 适配器（ADR-0038）。
 *
 * <p><strong>路径穿越防护</strong>：namespace 与 storage key 都会做清洗——解析目标路径前
 * 拒绝任何 {@code ..} 片段。storage key 是 namespace 目录下由服务端生成的 UUID，
 * 既避免冲突，也防止原始文件名进入文件系统。
 */
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path baseDirectory;

    public LocalFileStorageAdapter(String baseDirectory) {
        this.baseDirectory = Paths.get(baseDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage base directory: " + this.baseDirectory, e);
        }
    }

    @Override
    public StoredFile store(String namespace, String filename, @Nullable String contentType, InputStream content) {
        validateNamespace(namespace);
        Path nsDir = baseDirectory.resolve(namespace).normalize();
        if (!nsDir.startsWith(baseDirectory)) {
            throw new BusinessException(StorageErrorCode.INVALID_NAMESPACE);
        }
        String generatedKey = namespace + "/" + dev.ainer.core.uuid.Uuidv7.generate();
        Path target = baseDirectory.resolve(generatedKey).normalize();
        if (!target.startsWith(baseDirectory)) {
            throw new BusinessException(StorageErrorCode.INVALID_KEY);
        }
        try {
            Files.createDirectories(target.getParent());
            long size = Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(generatedKey, namespace, filename, contentType, size);
        } catch (IOException e) {
            throw new BusinessException(StorageErrorCode.STORE_FAILED, e);
        }
    }

    @Override
    public Optional<InputStream> resolve(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey");
        validateNoTraversal(storageKey);
        Path target = baseDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(baseDirectory)) {
            throw new BusinessException(StorageErrorCode.INVALID_KEY);
        }
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(target));
        } catch (IOException e) {
            throw new BusinessException(StorageErrorCode.RESOLVE_FAILED, e);
        }
    }

    @Override
    public boolean delete(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey");
        validateNoTraversal(storageKey);
        Path target = baseDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(baseDirectory)) {
            throw new BusinessException(StorageErrorCode.INVALID_KEY);
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BusinessException(StorageErrorCode.DELETE_FAILED, e);
        }
    }

    private static void validateNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.isEmpty() || namespace.contains("..")
                || namespace.contains("/") || namespace.contains("\\")
                || namespace.startsWith(".")) {
            throw new BusinessException(StorageErrorCode.INVALID_NAMESPACE);
        }
    }

    private static void validateNoTraversal(String value) {
        if (value.contains("..")) {
            throw new BusinessException(StorageErrorCode.INVALID_KEY);
        }
    }
}
