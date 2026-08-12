package dev.ainer.module.dictionary.dictionary.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** Row mapping for {@code ainer_dictionary_item}. */
public class DictionaryItemRow {
    private UUID id;
    private UUID typeId;
    private String code;
    private String label;
    private String labelEn;
    private String value;
    private int sortIndex;
    private String status;
    private String cssClass;
    private String remark;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTypeId() { return typeId; }
    public void setTypeId(UUID typeId) { this.typeId = typeId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getLabelEn() { return labelEn; }
    public void setLabelEn(String labelEn) { this.labelEn = labelEn; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public int getSortIndex() { return sortIndex; }
    public void setSortIndex(int sortIndex) { this.sortIndex = sortIndex; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCssClass() { return cssClass; }
    public void setCssClass(String cssClass) { this.cssClass = cssClass; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
