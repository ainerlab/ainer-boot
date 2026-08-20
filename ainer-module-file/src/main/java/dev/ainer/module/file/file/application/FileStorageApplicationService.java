package dev.ainer.module.file.file.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.storage.FileStoragePort;
import dev.ainer.core.storage.StoredFile;
import dev.ainer.module.file.file.domain.FileAudit;
import dev.ainer.module.file.file.domain.FileObject;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件存储应用服务（ADR-0040）：上传/下载/删除，含元数据持久化、大小与内容类型限制、
 * SHA-256 校验和，以及同事务变更审计。
 *
 * <p>上传顺序：校验 scope 与限制 → 通过 {@link FileStoragePort} 写入字节（SPI 强制
 * 路径遍历防护）→ 用实际写入的大小对照配置上限复核 → 在一个事务中持久化元数据 +
 * UPLOADED 审计。若数据库写入失败，已存储的字节会被删除作为补偿（尽力而为）。
 *
 * <p>删除顺序：在一个事务中持久化 DELETED 审计 + 删除元数据行（审计的 {@code file_id}
 * 由外键置空），随后再删除物理字节。提交与物理删除之间若发生崩溃，只会留下不可达的
 * 孤立文件，绝不会留下指向缺失字节的悬空元数据、表现为损坏的下载
 * （resolve 为空时降级为 404）。
 */
@Service
public class FileStorageApplicationService {

    private final FileStoragePort storagePort;
    private final FileObjectRepository objectRepository;
    private final FileAuditRepository auditRepository;
    private final FileStorageProperties properties;
    private final Clock clock;

    public FileStorageApplicationService(
            FileStoragePort storagePort,
            FileObjectRepository objectRepository,
            FileAuditRepository auditRepository,
            FileStorageProperties properties,
            Clock clock) {
        this.storagePort = storagePort;
        this.objectRepository = objectRepository;
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public FileObject upload(
            AuthenticatedPrincipal principal,
            @Nullable String requestId,
            String namespace,
            @Nullable String filename,
            @Nullable String contentType,
            InputStream content) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(content, "content");
        requireScope(principal, FileAuthorities.WRITE);
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(FileErrorCode.EMPTY_FILENAME);
        }
        if (contentType == null || contentType.isBlank()
                || !properties.allowedContentTypes().contains(contentType)) {
            throw new BusinessException(FileErrorCode.CONTENT_TYPE_NOT_ALLOWED);
        }

        Instant now = clock.instant();
        MessageDigest digest = sha256();
        StoredFile stored = storagePort.store(
                namespace, filename.strip(), contentType, new DigestInputStream(content, digest));
        if (stored.contentLength() > properties.maxSizeBytes()) {
            storagePort.delete(stored.storageKey());
            throw new BusinessException(FileErrorCode.FILE_TOO_LARGE);
        }

        FileObject object = new FileObject(
                dev.ainer.core.uuid.Uuidv7.generate(),
                stored.storageKey(),
                namespace,
                filename.strip(),
                contentType,
                stored.contentLength(),
                HexFormat.of().formatHex(digest.digest()),
                null,
                principal.authority().issuer(),
                principal.isService() ? "SERVICE" : "USER",
                principal.subjectId(),
                now);
        try {
            objectRepository.insert(object);
            auditRepository.insert(new FileAudit(
                    dev.ainer.core.uuid.Uuidv7.generate(),
                    object.id(),
                    FileAudit.OPERATION_UPLOADED,
                    namespace,
                    object.uploadedByIssuer(),
                    object.uploadedByType(),
                    object.uploadedById(),
                    requestId,
                    now));
        } catch (RuntimeException failure) {
            storagePort.delete(stored.storageKey());
            throw failure;
        }
        return object;
    }

    @Transactional(readOnly = true)
    public DownloadedFile download(AuthenticatedPrincipal principal, UUID id) {
        Objects.requireNonNull(principal, "principal");
        requireScope(principal, FileAuthorities.READ);
        FileObject object = objectRepository.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new BusinessException(FileErrorCode.NOT_FOUND));
        InputStream content = storagePort.resolve(object.storageKey())
                .orElseThrow(() -> new BusinessException(FileErrorCode.NOT_FOUND));
        return new DownloadedFile(object, content);
    }

    @Transactional
    public void delete(AuthenticatedPrincipal principal, @Nullable String requestId, UUID id) {
        Objects.requireNonNull(principal, "principal");
        requireScope(principal, FileAuthorities.WRITE);
        FileObject object = objectRepository.findById(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new BusinessException(FileErrorCode.NOT_FOUND));
        auditRepository.insert(new FileAudit(
                dev.ainer.core.uuid.Uuidv7.generate(),
                object.id(),
                FileAudit.OPERATION_DELETED,
                object.namespace(),
                principal.authority().issuer(),
                principal.isService() ? "SERVICE" : "USER",
                principal.subjectId(),
                requestId,
                clock.instant()));
        objectRepository.deleteById(object.id());
        // 返回 false = 孤立文件清理场景：字节已不存在，元数据删除仍会成功。
        storagePort.delete(object.storageKey());
    }

    @Transactional(readOnly = true)
    public FilePage page(AuthenticatedPrincipal principal, @Nullable String namespace, int page, int size) {
        Objects.requireNonNull(principal, "principal");
        requireScope(principal, FileAuthorities.READ);
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(FileErrorCode.INVALID_PAGE);
        }
        long offset = (long) (page - 1) * size;
        FileObjectRepository.FilePageSlice slice =
                objectRepository.findPage(blankToNull(namespace), offset, size);
        return new FilePage(slice.items(), page, size, slice.total());
    }

    private static void requireScope(AuthenticatedPrincipal principal, String scope) {
        if (!principal.hasScope(scope)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    /** 已解析的文件及其打开的内容流；关闭流由调用方负责。 */
    public record DownloadedFile(FileObject object, InputStream content) {
    }
}
