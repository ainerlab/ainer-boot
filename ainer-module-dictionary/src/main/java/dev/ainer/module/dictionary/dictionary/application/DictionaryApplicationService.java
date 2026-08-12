package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for dictionary operations (ADR-0038). Provides tree-structured type management,
 * item CRUD, and an in-memory cache for read-heavy lookups. Cache is invalidated on any write.
 */
@Service
@Transactional
public class DictionaryApplicationService {

    private final DictionaryTypeRepository typeRepository;
    private final DictionaryItemRepository itemRepository;

    /** Cache: typeCode → list of active items. Invalidated on any write to types or items. */
    private final Map<String, List<DictionaryItem>> itemCache = new ConcurrentHashMap<>();

    public DictionaryApplicationService(
            DictionaryTypeRepository typeRepository, DictionaryItemRepository itemRepository) {
        this.typeRepository = typeRepository;
        this.itemRepository = itemRepository;
    }

    // ---- Type management ----

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

    @Transactional(readOnly = true)
    public List<DictionaryType> getChildTypes(UUID parentId) {
        return typeRepository.findByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public List<DictionaryType> getAllActiveTypes() {
        return typeRepository.findAllActive();
    }

    // ---- Item management ----

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
        UUID saved = itemRepository.save(item);
        invalidateCache();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DictionaryItem> getItemsByType(UUID typeId) {
        return itemRepository.findActiveByTypeId(typeId);
    }

    /**
     * Resolve items by type code with caching. The cache is keyed by type code and invalidated on
     * any write to types or items. This is the primary read path for frontend dropdowns.
     */
    @Transactional(readOnly = true)
    public List<DictionaryItem> resolveItemsByTypeCode(String typeCode) {
        Objects.requireNonNull(typeCode, "typeCode");
        return itemCache.computeIfAbsent(typeCode, code -> {
            List<DictionaryType> types = typeRepository.findAllActive();
            return types.stream()
                    .filter(t -> code.equals(t.code()))
                    .findFirst()
                    .map(t -> itemRepository.findActiveByTypeId(t.id()))
                    .orElse(List.of());
        });
    }

    private void invalidateCache() {
        itemCache.clear();
    }
}
