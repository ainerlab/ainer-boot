package dev.ainer.module.dictionary.dictionary.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** {@code ainer_dictionary_type} 的行映射。 */
public class DictionaryTypeRow {
    private UUID id;
    private UUID parentId;
    private String code;
    private String name;
    private String nameEn;
    private String description;
    private String status;
    private int sortIndex;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getSortIndex() { return sortIndex; }
    public void setSortIndex(int sortIndex) { this.sortIndex = sortIndex; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
