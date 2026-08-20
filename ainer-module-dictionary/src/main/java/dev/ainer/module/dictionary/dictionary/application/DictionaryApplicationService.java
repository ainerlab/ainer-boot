package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryAudit;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryItem;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryStatus;
import dev.ainer.module.dictionary.dictionary.domain.DictionaryType;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 字典操作的应用服务（ADR-0040 管理面硬化）。使用 Spring Cache 抽象（ADR-0039）——
 * 读多查询用 {@code @Cacheable}，写入时 {@code @CacheEvict}。缓存后端可替换：
 * Caffeine（本地，默认）或 Redis/Valkey（分布式）。
 *
 * <p>管理面（创建/更新/状态/分页）要求 {@link AuthenticatedPrincipal} 携带
 * {@code dictionary.read} / {@code dictionary.manage}；每次变更写入同事务的
 * {@link DictionaryAudit} 行。{@link #resolveItemsByTypeCode} 是内部产品读路径，
 * 有意不校验 scope。
 */
@Service
@Transactional
public class DictionaryApplicationService {

    public static final String CACHE_ITEMS_BY_TYPE = "dict:items";
    public static final String CACHE_CHILD_TYPES = "dict:children";

    private final DictionaryTypeRepository typeRepository;
    private final DictionaryItemRepository itemRepository;
    private final DictionaryAuditRepository auditRepository;
    private final Clock clock;

    public DictionaryApplicationService(
            DictionaryTypeRepository typeRepository,
            DictionaryItemRepository itemRepository,
            DictionaryAuditRepository auditRepository,
            Clock clock) {
        this.typeRepository = typeRepository;
        this.itemRepository = itemRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    // ---- 类型管理 ----

    @Caching(evict = {
            @CacheEvict(value = CACHE_ITEMS_BY_TYPE, allEntries = true),
            @CacheEvict(value = CACHE_CHILD_TYPES, allEntries = true)
    })
    public UUID createType(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID parentId,
            String code, String name, @Nullable String nameEn, @Nullable String description) {
        requireManage(principal);
        typeRepository.findActiveByCode(parentId, code).ifPresent(existing -> {
            throw new BusinessException(DictionaryErrorCode.TYPE_ALREADY_EXISTS);
        });
        if (parentId != null) {
            typeRepository.findById(parentId).orElseThrow(
                    () -> new BusinessException(DictionaryErrorCode.PARENT_NOT_FOUND));
        }
        UUID id = dev.ainer.core.uuid.Uuidv7.generate();
        DictionaryType type = new DictionaryType(id, parentId, code, name, nameEn, description,
                DictionaryStatus.ACTIVE, 0, 0);
        UUID saved = typeRepository.save(type);
        audit(principal, requestId, DictionaryAudit.OPERATION_TYPE_CREATED,
                DictionaryAudit.TARGET_TYPE, saved, "code=" + code);
        return saved;
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_ITEMS_BY_TYPE, allEntries = true),
            @CacheEvict(value = CACHE_CHILD_TYPES, allEntries = true)
    })
    public DictionaryType updateType(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID id,
            @Nullable String name, @Nullable String nameEn,
            @Nullable String description, @Nullable Integer sortIndex, long expectedVersion) {
        requireManage(principal);
        DictionaryType existing = typeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.TYPE_NOT_FOUND));
        if (!typeRepository.update(id, name, nameEn, description, sortIndex,
                expectedVersion, expectedVersion + 1)) {
            throw new BusinessException(DictionaryErrorCode.CONCURRENT_MODIFICATION);
        }
        audit(principal, requestId, DictionaryAudit.OPERATION_TYPE_UPDATED,
                DictionaryAudit.TARGET_TYPE, id, "code=" + existing.code());
        return typeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.TYPE_NOT_FOUND));
    }

    @Caching(evict = {
            @CacheEvict(value = CACHE_ITEMS_BY_TYPE, allEntries = true),
            @CacheEvict(value = CACHE_CHILD_TYPES, allEntries = true)
    })
    public DictionaryType changeTypeStatus(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID id,
            DictionaryStatus status, long expectedVersion) {
        requireManage(principal);
        DictionaryType existing = typeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.TYPE_NOT_FOUND));
        if (!typeRepository.updateStatus(id, status, expectedVersion, expectedVersion + 1)) {
            throw new BusinessException(DictionaryErrorCode.CONCURRENT_MODIFICATION);
        }
        audit(principal, requestId, DictionaryAudit.OPERATION_TYPE_STATUS_CHANGED,
                DictionaryAudit.TARGET_TYPE, id,
                "code=" + existing.code() + " status=" + status);
        return typeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.TYPE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<DictionaryType> getType(AuthenticatedPrincipal principal, UUID id) {
        requireRead(principal);
        return typeRepository.findById(id);
    }

    @Cacheable(value = CACHE_CHILD_TYPES, key = "#parentId", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<DictionaryType> getChildTypes(AuthenticatedPrincipal principal, UUID parentId) {
        requireRead(principal);
        return typeRepository.findByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public DictionaryPageSlice<DictionaryType> pageTypes(
            AuthenticatedPrincipal principal, @Nullable String status, int page, int size) {
        requireRead(principal);
        requirePage(page, size);
        return typeRepository.findPage(normalizeStatus(status), (long) (page - 1) * size, size);
    }

    // ---- 字典项管理 ----

    @CacheEvict(value = CACHE_ITEMS_BY_TYPE, allEntries = true)
    public UUID createItem(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID typeId,
            String code, String label, @Nullable String labelEn, String value,
            int sortIndex, @Nullable String cssClass, @Nullable String remark) {
        requireManage(principal);
        typeRepository.findById(typeId).orElseThrow(
                () -> new BusinessException(DictionaryErrorCode.TYPE_NOT_FOUND));
        itemRepository.findActiveByCode(typeId, code).ifPresent(existing -> {
            throw new BusinessException(DictionaryErrorCode.ITEM_ALREADY_EXISTS);
        });
        UUID id = dev.ainer.core.uuid.Uuidv7.generate();
        DictionaryItem item = new DictionaryItem(id, typeId, code, label, labelEn, value, sortIndex,
                DictionaryStatus.ACTIVE, cssClass, remark, 0);
        UUID saved = itemRepository.save(item);
        audit(principal, requestId, DictionaryAudit.OPERATION_ITEM_CREATED,
                DictionaryAudit.TARGET_ITEM, saved, "code=" + code);
        return saved;
    }

    @CacheEvict(value = CACHE_ITEMS_BY_TYPE, allEntries = true)
    public DictionaryItem updateItem(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID id,
            @Nullable String label, @Nullable String labelEn, @Nullable String value,
            @Nullable Integer sortIndex, @Nullable String cssClass, @Nullable String remark,
            long expectedVersion) {
        requireManage(principal);
        DictionaryItem existing = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.ITEM_NOT_FOUND));
        if (!itemRepository.update(id, label, labelEn, value, sortIndex, cssClass, remark,
                expectedVersion, expectedVersion + 1)) {
            throw new BusinessException(DictionaryErrorCode.CONCURRENT_MODIFICATION);
        }
        audit(principal, requestId, DictionaryAudit.OPERATION_ITEM_UPDATED,
                DictionaryAudit.TARGET_ITEM, id, "code=" + existing.code());
        return itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.ITEM_NOT_FOUND));
    }

    @CacheEvict(value = CACHE_ITEMS_BY_TYPE, allEntries = true)
    public DictionaryItem changeItemStatus(
            AuthenticatedPrincipal principal, @Nullable String requestId, UUID id,
            DictionaryStatus status, long expectedVersion) {
        requireManage(principal);
        DictionaryItem existing = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.ITEM_NOT_FOUND));
        if (!itemRepository.updateStatus(id, status, expectedVersion, expectedVersion + 1)) {
            throw new BusinessException(DictionaryErrorCode.CONCURRENT_MODIFICATION);
        }
        audit(principal, requestId, DictionaryAudit.OPERATION_ITEM_STATUS_CHANGED,
                DictionaryAudit.TARGET_ITEM, id,
                "code=" + existing.code() + " status=" + status);
        return itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DictionaryErrorCode.ITEM_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<DictionaryItem> getItemsByType(AuthenticatedPrincipal principal, UUID typeId) {
        requireRead(principal);
        return itemRepository.findActiveByTypeId(typeId);
    }

    @Transactional(readOnly = true)
    public Optional<DictionaryItem> getItem(AuthenticatedPrincipal principal, UUID id) {
        requireRead(principal);
        return itemRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public DictionaryPageSlice<DictionaryItem> pageItems(
            AuthenticatedPrincipal principal, UUID typeId, int page, int size) {
        requireRead(principal);
        requirePage(page, size);
        return itemRepository.findPage(Objects.requireNonNull(typeId, "typeId"),
                (long) (page - 1) * size, size);
    }

    // ---- 内部产品读路径（设计上不校验 scope）----

    /**
     * 按类型编码解析字典项并缓存。产品下拉框的主读路径；
     * 不经管理 HTTP 面暴露。
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

    // ---- 辅助方法 ----

    private static void requireManage(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        if (!principal.hasScope(DictionaryAuthorities.MANAGE)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requireRead(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        if (!principal.hasScope(DictionaryAuthorities.READ)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(DictionaryErrorCode.INVALID_PAGE);
        }
    }

    private static String normalizeStatus(@Nullable String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String stripped = status.strip().toUpperCase();
        try {
            DictionaryStatus.valueOf(stripped);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(DictionaryErrorCode.INVALID_REQUEST);
        }
        return stripped;
    }

    private void audit(
            AuthenticatedPrincipal principal, @Nullable String requestId,
            String operation, String targetKind, UUID targetId, @Nullable String detail) {
        auditRepository.insert(new DictionaryAudit(
                dev.ainer.core.uuid.Uuidv7.generate(),
                operation,
                targetKind,
                targetId,
                principal.authority().issuer(),
                principal.isService() ? "SERVICE" : "USER",
                principal.subjectId(),
                requestId,
                detail,
                clock.instant()));
    }
}
