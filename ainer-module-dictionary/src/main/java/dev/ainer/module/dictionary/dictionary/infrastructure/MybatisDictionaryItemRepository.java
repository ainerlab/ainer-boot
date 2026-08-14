package dev.ainer.module.dictionary.dictionary.infrastructure;

import dev.ainer.module.dictionary.dictionary.application.DictionaryItemRepository;
import dev.ainer.module.dictionary.dictionary.application.DictionaryPageSlice;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MybatisDictionaryItemRepository implements DictionaryItemRepository {

    private final DictionaryItemMapper mapper;

    public MybatisDictionaryItemRepository(DictionaryItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID save(DictionaryItem item) {
        DictionaryItemRow row = new DictionaryItemRow();
        row.setId(item.id());
        row.setTypeId(item.typeId());
        row.setCode(item.code());
        row.setLabel(item.label());
        row.setLabelEn(item.labelEn());
        row.setValue(item.value());
        row.setSortIndex(item.sortIndex());
        row.setStatus(item.status().name());
        row.setCssClass(item.cssClass());
        row.setRemark(item.remark());
        row.setVersion(item.version());
        return mapper.insertReturningId(row, Instant.now());
    }

    @Override
    public Optional<DictionaryItem> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisDictionaryItemRepository::toDomain);
    }

    @Override
    public Optional<DictionaryItem> findActiveByCode(UUID typeId, String code) {
        return Optional.ofNullable(mapper.selectActiveByCode(typeId, code))
                .map(MybatisDictionaryItemRepository::toDomain);
    }

    @Override
    public List<DictionaryItem> findActiveByTypeId(UUID typeId) {
        return mapper.selectActiveByTypeId(typeId).stream()
                .map(MybatisDictionaryItemRepository::toDomain).toList();
    }

    @Override
    public boolean update(UUID id, @Nullable String label, @Nullable String labelEn,
            @Nullable String value, @Nullable Integer sortIndex,
            @Nullable String cssClass, @Nullable String remark,
            long expectedVersion, long newVersion) {
        return mapper.update(id, label, labelEn, value, sortIndex, cssClass, remark,
                expectedVersion, newVersion, Instant.now()) > 0;
    }

    @Override
    public boolean updateStatus(UUID id, DictionaryStatus status, long expectedVersion, long newVersion) {
        return mapper.updateStatus(id, status.name(), expectedVersion, newVersion, Instant.now()) > 0;
    }

    @Override
    public DictionaryPageSlice<DictionaryItem> findPage(UUID typeId, long offset, int size) {
        List<DictionaryItem> items = mapper.selectPage(typeId, offset, size).stream()
                .map(MybatisDictionaryItemRepository::toDomain).toList();
        return new DictionaryPageSlice<>(items, mapper.countPage(typeId));
    }

    private static DictionaryItem toDomain(DictionaryItemRow row) {
        return new DictionaryItem(
                row.getId(), row.getTypeId(), row.getCode(), row.getLabel(), row.getLabelEn(),
                row.getValue(), row.getSortIndex(), DictionaryStatus.valueOf(row.getStatus()),
                row.getCssClass(), row.getRemark(), row.getVersion());
    }
}
