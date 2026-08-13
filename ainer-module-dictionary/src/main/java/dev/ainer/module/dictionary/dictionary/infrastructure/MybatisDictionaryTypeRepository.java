package dev.ainer.module.dictionary.dictionary.infrastructure;

import dev.ainer.module.dictionary.dictionary.application.DictionaryTypeRepository;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private static DictionaryType toDomain(DictionaryTypeRow row) {
        return new DictionaryType(
                row.getId(), row.getParentId(), row.getCode(), row.getName(), row.getNameEn(),
                row.getDescription(), DictionaryStatus.valueOf(row.getStatus()),
                row.getSortIndex(), row.getVersion());
    }
}
