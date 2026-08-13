package dev.ainer.module.config.config.infrastructure;

import dev.ainer.module.config.config.application.ConfigEntryRepository;
import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigValueType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisConfigEntryRepository implements ConfigEntryRepository {

    private final ConfigEntryMapper mapper;

    public MybatisConfigEntryRepository(ConfigEntryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID save(ConfigEntry entry) {
        ConfigEntryRow row = toRow(entry);
        return mapper.insertReturningId(row, Instant.now());
    }

    @Override
    public Optional<ConfigEntry> findByNamespaceAndKey(String namespace, String key) {
        return Optional.ofNullable(mapper.selectByNamespaceAndKey(namespace, key))
                .map(MybatisConfigEntryRepository::toDomain);
    }

    @Override
    public List<ConfigEntry> findByNamespace(String namespace) {
        return mapper.selectByNamespace(namespace).stream()
                .map(MybatisConfigEntryRepository::toDomain).toList();
    }

    @Override
    public boolean update(UUID id, String value, String encryptedValue,
                          long expectedVersion, long newVersion) {
        int affected = mapper.updateValue(id, value, encryptedValue, expectedVersion, newVersion, Instant.now());
        return affected == 1;
    }

    private static ConfigEntryRow toRow(ConfigEntry entry) {
        ConfigEntryRow row = new ConfigEntryRow();
        row.setId(entry.id());
        row.setNamespace(entry.namespace());
        row.setConfigKey(entry.key());
        row.setConfigValue(entry.value());
        row.setValueType(entry.valueType().name());
        row.setSecret(entry.secret());
        row.setEncryptedValue(entry.encryptedValue());
        row.setDescription(entry.description());
        row.setVersion(entry.version());
        return row;
    }

    private static ConfigEntry toDomain(ConfigEntryRow row) {
        return new ConfigEntry(
                row.getId(), row.getNamespace(), row.getConfigKey(), row.getConfigValue(),
                ConfigValueType.valueOf(row.getValueType()), row.isSecret(),
                row.getEncryptedValue(), row.getDescription(), row.getVersion());
    }
}
