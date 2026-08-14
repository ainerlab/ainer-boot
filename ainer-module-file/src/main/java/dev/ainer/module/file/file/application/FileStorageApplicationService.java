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
 * File storage application service (ADR-0040): upload/download/delete with metadata persistence,
 * size and content-type limits, SHA-256 checksum, and same-transaction change audit.
 *
 * <p>Upload order: validate scope and limits → store bytes via {@link FileStoragePort} (the SPI
 * enforces path-traversal protection) → verify the actually-written size against the configured
 * ceiling → persist metadata + UPLOADED audit in one transaction. If the database write fails the
 * already-stored bytes are deleted as compensation (best effort).
 *
 * <p>Delete order: persist DELETED audit + delete metadata row in one transaction (the audit's
 * {@code file_id} is nulled by the FK), then remove the physical bytes. A crash between commit and
 * physical delete leaves an unreachable orphan file, never dangling metadata pointing at missing
 * bytes surfaced as a broken download (resolve-empty degrades to 404).
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
        // false = orphan cleanup: bytes already gone, metadata removal still succeeds.
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

    /** A resolved file plus its open content stream; the caller owns closing the stream. */
    public record DownloadedFile(FileObject object, InputStream content) {
    }
}
