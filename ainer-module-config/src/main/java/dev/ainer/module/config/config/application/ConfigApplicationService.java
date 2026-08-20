package dev.ainer.module.config.config.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigHistory;
import dev.ainer.module.config.config.domain.ConfigValueType;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 动态配置的应用服务（ADR-0038）。使用 Spring Cache 抽象（ADR-0039）——
 * 读路径 {@code @Cacheable}，写入时 {@code @CacheEvict}。缓存后端可替换：
 * Caffeine（本地，默认）或 Redis/Valkey（分布式）。
 *
 * <p>secret 值通过 {@link ConfigEncryptionPort} 加密（默认 AES-GCM）。调用方向
 * {@link #setSecret} 提供<strong>明文</strong>；服务在持久化前完成加密。
 * {@link #getSecret} 解密并返回明文。原始密文绝不暴露给调用方。
 */
@Service
@Transactional
public class ConfigApplicationService {

    public static final String CACHE_CONFIG_ENTRY = "config:entry";

    private final ConfigEntryRepository entryRepository;
    private final ConfigHistoryRepository historyRepository;
    private final ConfigEncryptionPort encryption;
    private final Clock clock;

    public ConfigApplicationService(
            ConfigEntryRepository entryRepository,
            ConfigHistoryRepository historyRepository,
            ConfigEncryptionPort encryption,
            Clock clock) {
        this.entryRepository = entryRepository;
        this.historyRepository = historyRepository;
        this.encryption = encryption;
        this.clock = clock;
    }

    // ---- 写入 ----

    @CacheEvict(value = CACHE_CONFIG_ENTRY, key = "#namespace + ':' + #key")
    public void setValue(
            String namespace, String key, String value, ConfigValueType valueType,
            @Nullable String description, @Nullable AuthenticatedPrincipal changedBy) {
        Objects.requireNonNull(value, "value");
        requireManage(changedBy);
        Optional<ConfigEntry> existing = entryRepository.findByNamespaceAndKey(namespace, key);
        if (existing.isPresent()) {
            ConfigEntry entry = existing.get();
            if (entry.secret()) {
                throw new BusinessException(ConfigErrorCode.PLAINTEXT_ON_SECRET_KEY);
            }
            long newVersion = entry.version() + 1;
            entryRepository.update(entry.id(), value, null, entry.version(), newVersion);
            recordHistory(entry, entry.value(), value, entry.version(), newVersion, changedBy);
        } else {
            UUID id = dev.ainer.core.uuid.Uuidv7.generate();
            ConfigEntry entry = new ConfigEntry(id, namespace, key, value, valueType, false, null,
                    description, 0);
            entryRepository.save(entry);
            recordHistory(entry, null, value, null, 0L, changedBy);
        }
    }

    /**
     * 设置 secret 配置值。{@code plaintext} 在持久化前经 {@link ConfigEncryptionPort}
     * 加密——绝不存储原始明文。
     */
    @CacheEvict(value = CACHE_CONFIG_ENTRY, key = "#namespace + ':' + #key")
    public void setSecret(
            String namespace, String key, String plaintext, ConfigValueType valueType,
            @Nullable String description, @Nullable AuthenticatedPrincipal changedBy) {
        Objects.requireNonNull(plaintext, "plaintext");
        requireManage(changedBy);
        String ciphertext = encryption.encrypt(plaintext);
        Optional<ConfigEntry> existing = entryRepository.findByNamespaceAndKey(namespace, key);
        if (existing.isPresent()) {
            ConfigEntry entry = existing.get();
            long newVersion = entry.version() + 1;
            entryRepository.update(entry.id(), null, ciphertext, entry.version(), newVersion);
            recordHistory(entry, null, "[encrypted]", entry.version(), newVersion, changedBy);
        } else {
            UUID id = dev.ainer.core.uuid.Uuidv7.generate();
            ConfigEntry entry = new ConfigEntry(id, namespace, key, null, valueType, true,
                    ciphertext, description, 0);
            entryRepository.save(entry);
            recordHistory(entry, null, "[encrypted]", null, 0L, changedBy);
        }
    }

    // ---- 读取（缓存）----

    @Cacheable(value = CACHE_CONFIG_ENTRY, key = "#namespace + ':' + #key", unless = "#result == null || !#result.isPresent()")
    @Transactional(readOnly = true)
    public Optional<ConfigEntry> getEntry(String namespace, String key) {
        return entryRepository.findByNamespaceAndKey(namespace, key);
    }

    @Transactional(readOnly = true)
    public Optional<String> getValue(String namespace, String key) {
        return getEntry(namespace, key).filter(e -> !e.secret()).map(ConfigEntry::value);
    }

    @Transactional(readOnly = true)
    public <T> Optional<T> getTyped(String namespace, String key, Class<T> type) {
        return getValue(namespace, key).map(raw -> parseType(raw, type));
    }

    /**
     * 读取 secret 配置值并解密为明文。密文经 {@link ConfigEncryptionPort} 解密。
     */
    @Transactional(readOnly = true)
    public Optional<String> getSecret(String namespace, String key) {
        return getEntry(namespace, key)
                .filter(ConfigEntry::secret)
                .map(e -> encryption.decrypt(e.encryptedValue()));
    }

    @Transactional(readOnly = true)
    public List<ConfigEntry> getByNamespace(String namespace) {
        return entryRepository.findByNamespace(namespace);
    }

    /** 管理读路径：要求已验证主体携带 {@code config.read}。 */
    @Transactional(readOnly = true)
    public List<ConfigEntry> getByNamespace(AuthenticatedPrincipal principal, String namespace) {
        requireRead(principal);
        return entryRepository.findByNamespace(namespace);
    }

    @Transactional(readOnly = true)
    public List<ConfigHistory> getHistory(String namespace, String key) {
        return entryRepository.findByNamespaceAndKey(namespace, key)
                .map(entry -> historyRepository.findByEntryId(entry.id()))
                .orElse(List.of());
    }

    /** 管理读路径：要求已验证主体携带 {@code config.read}。 */
    @Transactional(readOnly = true)
    public List<ConfigHistory> getHistory(
            AuthenticatedPrincipal principal, String namespace, String key) {
        requireRead(principal);
        return getHistory(namespace, key);
    }

    // ---- 内部方法 ----

    private static void requireManage(@Nullable AuthenticatedPrincipal principal) {
        if (principal != null && !principal.hasScope(ConfigAuthorities.MANAGE)) {
            throw new BusinessException(dev.ainer.core.error.StandardErrorCode.FORBIDDEN);
        }
    }

    private static void requireRead(AuthenticatedPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        if (!principal.hasScope(ConfigAuthorities.READ)) {
            throw new BusinessException(dev.ainer.core.error.StandardErrorCode.FORBIDDEN);
        }
    }

    private void recordHistory(
            ConfigEntry entry, @Nullable String oldValue, @Nullable String newValue,
            @Nullable Long oldVersion, @Nullable Long newVersion,
            @Nullable AuthenticatedPrincipal changedBy) {
        historyRepository.insert(new ConfigHistory(
                dev.ainer.core.uuid.Uuidv7.generate(),
                entry.id(),
                entry.namespace(),
                entry.key(),
                oldValue,
                newValue,
                oldVersion,
                newVersion,
                changedBy != null ? changedBy.authority().issuer() : null,
                changedBy != null ? (changedBy.isService() ? "SERVICE" : "USER") : null,
                changedBy != null ? changedBy.subjectId() : null,
                clock.instant()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseType(String raw, Class<T> type) {
        if (type == String.class) return (T) raw;
        if (type == Integer.class || type == int.class) return (T) Integer.valueOf(raw);
        if (type == Long.class || type == long.class) return (T) Long.valueOf(raw);
        if (type == Boolean.class || type == boolean.class) return (T) Boolean.valueOf(raw);
        if (type == java.math.BigDecimal.class) return (T) new java.math.BigDecimal(raw);
        throw new IllegalArgumentException("Unsupported config type: " + type);
    }
}
