package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for dictionary operations (ADR-0038). Uses Spring Cache abstraction
 * (ADR-0039) — {@code @Cacheable} for read-heavy lookups, {@code @CacheEvict} on writes.
 * Cache backend is swappable: Caffeine (local, default) or Redis/Valkey (distributed).
 *
 * <p>Cache keys:
 * <ul>
 *   <li>{@code dict:items:{typeCode}} — items by type code (frontend dropdowns);</li>
 *   <li>{@code dict:children:{parentId}} — child types of a parent.</li>
 * </ul>
 * All caches are evicted on any write to types or items.
 */
@Service
@Transactional
public class DictionaryApplicationService {

    public static final String CACHE_ITEMS_BY_TYPE = "dict:items";
    public static final String CACHE_CHILD_TYPES = "dict:children";

    private final DictionaryTypeRepository typeRepository;
    private final DictionaryItemRepository itemRepository;

    public DictionaryApplicationService(
            DictionaryTypeRepository typeRepository, DictionaryItemRepository itemRepository) {
        this.typeRepository = typeRepository;
        this.itemRepository = itemRepository;
    }

    // ---- Type management ----

    @Caching(evict = {
            @CacheEvict(value = CACHE_ITEMS_BY_TYPE, allEntries = true),
            @CacheEvict(value = CACHE_CHILD_TYPES, allEntries = true)
    })
    public UUID createType(UUID parentId, String code, String name, String nameEn, String description) {
        typeRepository.findActiveByCode(parentId, code).ifPresent(existing -> {
            throw new IllegalArgumentException("Dictionary type already exists: " + code);
        });
        if (parentId != null) {
            typeRepository.findById(parentId).orElseThrow(() ->
                    new IllegalArgumentException("Parent type not found: " + parentId));
        }
        UUID id = UUID.randomUUID();
        DictionaryType type = new DictionaryType(id, parentId, code, name, nameEn, description,
                DictionaryStatus.ACTIVE, 0, 0);
        return typeRepository.save(type);
    }

    @Transactional(readOnly = true)
    public Optional<DictionaryType> getType(UUID id) {
        return typeRepository.findById(id);
    }

    @Cacheable(value = CACHE_CHILD_TYPES, key = "#parentId", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<DictionaryType> getChildTypes(UUID parentId) {
        return typeRepository.findByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public List<DictionaryType> getAllActiveTypes() {
        return typeRepository.findAllActive();
    }

    // ---- Item management ----

    @CacheEvict(value = CACHE_ITEMS_BY_TYPE, allEntries = true)
    public UUID createItem(UUID typeId, String code, String label, String labelEn, String value,
                           int sortIndex, String cssClass, String remark) {
        typeRepository.findById(typeId).orElseThrow(() ->
                new IllegalArgumentException("Dictionary type not found: " + typeId));
        itemRepository.findActiveByCode(typeId, code).ifPresent(existing -> {
            throw new IllegalArgumentException("Dictionary item already exists: " + code);
        });
        UUID id = UUID.randomUUID();
        DictionaryItem item = new DictionaryItem(id, typeId, code, label, labelEn, value, sortIndex,
                DictionaryStatus.ACTIVE, cssClass, remark, 0);
        return itemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<DictionaryItem> getItemsByType(UUID typeId) {
        return itemRepository.findActiveByTypeId(typeId);
    }

    /**
     * Resolve items by type code with caching. Primary read path for frontend dropdowns.
     * Cached via Spring Cache (Caffeine or Redis depending on {@code ainer.cache.type}).
     */
    @Cacheable(value = CACHE_ITEMS_BY_TYPE, key = "#typeCode", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<DictionaryItem> resolveItemsByTypeCode(String typeCode) {
        List<DictionaryType> types = typeRepository.findAllActive();
        return types.stream()
                .filter(t -> typeCode.equals(t.code()))
                .findFirst()
                .map(t -> itemRepository.findActiveByTypeId(t.id()))
                .orElse(List.of());
    }
}
