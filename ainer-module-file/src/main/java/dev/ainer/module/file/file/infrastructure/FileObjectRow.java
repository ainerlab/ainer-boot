package dev.ainer.module.file.file.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** Row mapping for {@code ainer_file_object}. */
public class FileObjectRow {
    private UUID id;
    private String storageKey;
    private String namespace;
    private String filename;
    private String contentType;
    private long contentLength;
    private String checksumSha256;
    private UUID workspaceId;
    private String uploadedByIssuer;
    private String uploadedByType;
    private String uploadedById;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getContentLength() { return contentLength; }
    public void setContentLength(long contentLength) { this.contentLength = contentLength; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getUploadedByIssuer() { return uploadedByIssuer; }
    public void setUploadedByIssuer(String uploadedByIssuer) { this.uploadedByIssuer = uploadedByIssuer; }
    public String getUploadedByType() { return uploadedByType; }
    public void setUploadedByType(String uploadedByType) { this.uploadedByType = uploadedByType; }
    public String getUploadedById() { return uploadedById; }
    public void setUploadedById(String uploadedById) { this.uploadedById = uploadedById; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
