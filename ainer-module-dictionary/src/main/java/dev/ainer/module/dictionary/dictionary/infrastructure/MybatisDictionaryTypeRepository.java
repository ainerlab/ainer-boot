package dev.ainer.module.dictionary.dictionary.infrastructure;

import dev.ainer.module.dictionary.dictionary.application.DictionaryPageSlice;
import dev.ainer.module.dictionary.dictionary.application.DictionaryTypeRepository;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DictionaryTypeRepository} 的 MyBatis 适配器，对应表 {@code ainer_dictionary_type}。
 * 领域对象与行对象双向映射；乐观锁更新与状态迁移由 mapper 语句按版本条件完成，
 * 影响行数转成 boolean 表示更新是否命中。
 */
@Repository
public class MybatisDictionaryTypeRepository implements DictionaryTypeRepository {

    private final DictionaryTypeMapper mapper;

    public MybatisDictionaryTypeRepository(DictionaryTypeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID save(DictionaryType type) {
        DictionaryTypeRow row = new DictionaryTypeRow();
        row.setId(type.id());
        row.setParentId(type.parentId());
        row.setCode(type.code());
        row.setName(type.name());
        row.setNameEn(type.nameEn());
        row.setDescription(type.description());
        row.setStatus(type.status().name());
        row.setSortIndex(type.sortIndex());
        row.setVersion(type.version());
        return mapper.insertReturningId(row, Instant.now());
    }

    @Override
    public Optional<DictionaryType> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisDictionaryTypeRepository::toDomain);
    }

    @Override
    public Optional<DictionaryType> findActiveByCode(UUID parentId, String code) {
        return Optional.ofNullable(mapper.selectActiveByCode(parentId, code))
                .map(MybatisDictionaryTypeRepository::toDomain);
    }

    @Override
    public List<DictionaryType> findByParentId(UUID parentId) {
        return mapper.selectByParentId(parentId).stream().map(MybatisDictionaryTypeRepository::toDomain).toList();
    }

    @Override
    public List<DictionaryType> findAllActive() {
        return mapper.selectAllActive().stream().map(MybatisDictionaryTypeRepository::toDomain).toList();
    }

    @Override
    public Collection<DictionaryType> findAll() {
        return mapper.selectAll().stream().map(MybatisDictionaryTypeRepository::toDomain).toList();
    }

    @Override
    public boolean update(UUID id, @Nullable String name, @Nullable String nameEn,
            @Nullable String description, @Nullable Integer sortIndex,
            long expectedVersion, long newVersion) {
        return mapper.update(id, name, nameEn, description, sortIndex,
                expectedVersion, newVersion, Instant.now()) > 0;
    }

    @Override
    public boolean updateStatus(UUID id, DictionaryStatus status,
            long expectedVersion, long newVersion) {
        return mapper.updateStatus(id, status.name(), expectedVersion, newVersion, Instant.now()) > 0;
    }

    @Override
    public DictionaryPageSlice<DictionaryType> findPage(@Nullable String status, long offset, int size) {
        List<DictionaryType> items = mapper.selectPage(status, offset, size).stream()
                .map(MybatisDictionaryTypeRepository::toDomain).toList();
        return new DictionaryPageSlice<>(items, mapper.countPage(status));
    }

    private static DictionaryType toDomain(DictionaryTypeRow row) {
        return new DictionaryType(
                row.getId(), row.getParentId(), row.getCode(), row.getName(), row.getNameEn(),
                row.getDescription(), DictionaryStatus.valueOf(row.getStatus()),
                row.getSortIndex(), row.getVersion());
    }
}
