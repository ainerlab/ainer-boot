package dev.ainer.module.config.config.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** {@code ainer_config_entry} 的行映射。 */
public class ConfigEntryRow {
    private UUID id;
    private String namespace;
    private String configKey;
    private String configValue;
    private String valueType;
    private boolean isSecret;
    private String encryptedValue;
    private String description;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public boolean isSecret() { return isSecret; }
    public void setSecret(boolean secret) { isSecret = secret; }
    public String getEncryptedValue() { return encryptedValue; }
    public void setEncryptedValue(String encryptedValue) { this.encryptedValue = encryptedValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
