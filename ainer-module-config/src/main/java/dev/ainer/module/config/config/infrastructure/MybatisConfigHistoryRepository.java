package dev.ainer.module.config.config.infrastructure;

import dev.ainer.module.config.config.application.ConfigHistoryRepository;
import dev.ainer.module.config.config.domain.ConfigHistory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MybatisConfigHistoryRepository implements ConfigHistoryRepository {

    private final ConfigHistoryMapper mapper;

    public MybatisConfigHistoryRepository(ConfigHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ConfigHistory history) {
        ConfigHistoryRow row = new ConfigHistoryRow();
        row.setId(history.id());
        row.setEntryId(history.entryId());
        row.setNamespace(history.namespace());
        row.setConfigKey(history.key());
        row.setOldValue(history.oldValue());
        row.setNewValue(history.newValue());
        row.setOldVersion(history.oldVersion());
        row.setNewVersion(history.newVersion());
        row.setChangedByIssuer(history.changedByIssuer());
        row.setChangedByType(history.changedByType());
        row.setChangedById(history.changedById());
        row.setChangedAt(history.changedAt());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException("Config history insert affected unexpected row count");
        }
    }

    @Override
    public List<ConfigHistory> findByEntryId(UUID entryId) {
        return mapper.selectByEntryId(entryId).stream().map(row -> new ConfigHistory(
                row.getId(), row.getEntryId(), row.getNamespace(), row.getConfigKey(),
                row.getOldValue(), row.getNewValue(), row.getOldVersion(), row.getNewVersion(),
                row.getChangedByIssuer(), row.getChangedByType(), row.getChangedById(),
                row.getChangedAt())).toList();
    }
}
