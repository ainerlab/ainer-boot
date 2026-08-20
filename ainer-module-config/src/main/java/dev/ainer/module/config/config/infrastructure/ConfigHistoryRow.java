package dev.ainer.module.config.config.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** {@code ainer_config_history} 的行映射。 */
public class ConfigHistoryRow {
    private UUID id;
    private UUID entryId;
    private String namespace;
    private String configKey;
    private String oldValue;
    private String newValue;
    private Long oldVersion;
    private Long newVersion;
    private String changedByIssuer;
    private String changedByType;
    private String changedById;
    private Instant changedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID entryId) { this.entryId = entryId; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public Long getOldVersion() { return oldVersion; }
    public void setOldVersion(Long oldVersion) { this.oldVersion = oldVersion; }
    public Long getNewVersion() { return newVersion; }
    public void setNewVersion(Long newVersion) { this.newVersion = newVersion; }
    public String getChangedByIssuer() { return changedByIssuer; }
    public void setChangedByIssuer(String changedByIssuer) { this.changedByIssuer = changedByIssuer; }
    public String getChangedByType() { return changedByType; }
    public void setChangedByType(String changedByType) { this.changedByType = changedByType; }
    public String getChangedById() { return changedById; }
    public void setChangedById(String changedById) { this.changedById = changedById; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
