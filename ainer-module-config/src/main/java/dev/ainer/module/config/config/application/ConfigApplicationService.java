package dev.ainer.module.config.config.application;

import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigHistory;
import dev.ainer.module.config.config.domain.ConfigValueType;
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
 * Application service for dynamic configuration (ADR-0038). Uses Spring Cache abstraction
 * (ADR-0039) — {@code @Cacheable} for read path, {@code @CacheEvict} on writes. Cache backend
 * is swappable: Caffeine (local, default) or Redis/Valkey (distributed).
 *
 * <p>Cache keys:
 * <ul>
 *   <li>{@code config:entry:{namespace}:{key}} — full ConfigEntry (for getValue/getSecret);</li>
 * </ul>
 * Evicted on any setValue/setSecret write.
 *
 * <p>Secret values are never stored in plaintext. The caller supplies the plaintext when setting a
 * secret; the service stores the encrypted ciphertext.
 */
@Service
@Transactional
public class ConfigApplicationService {

    public static final String CACHE_CONFIG_ENTRY = "config:entry";

    private final ConfigEntryRepository entryRepository;
    private final ConfigHistoryRepository historyRepository;
    private final Clock clock;

    public ConfigApplicationService(
            ConfigEntryRepository entryRepository,
            ConfigHistoryRepository historyRepository,
            Clock clock) {
        this.entryRepository = entryRepository;
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    // ---- Write ----

    @CacheEvict(value = CACHE_CONFIG_ENTRY, key = "#namespace + ':' + #key")
    public void setValue(
            String namespace, String key, String value, ConfigValueType valueType,
            @Nullable String description, @Nullable AuthenticatedPrincipal changedBy) {
        Objects.requireNonNull(value, "value");
        Optional<ConfigEntry> existing = entryRepository.findByNamespaceAndKey(namespace, key);
        if (existing.isPresent()) {
            ConfigEntry entry = existing.get();
            if (entry.secret()) {
                throw new IllegalArgumentException("Cannot set plaintext value for a secret key: " + key);
            }
            long newVersion = entry.version() + 1;
            entryRepository.update(entry.id(), value, null, entry.version(), newVersion);
            recordHistory(entry, entry.value(), value, entry.version(), newVersion, changedBy);
        } else {
            UUID id = UUID.randomUUID();
            ConfigEntry entry = new ConfigEntry(id, namespace, key, value, valueType, false, null,
                    description, 0);
            entryRepository.save(entry);
            recordHistory(entry, null, value, null, 0L, changedBy);
        }
    }

    @CacheEvict(value = CACHE_CONFIG_ENTRY, key = "#namespace + ':' + #key")
    public void setSecret(
            String namespace, String key, String encryptedValue, ConfigValueType valueType,
            @Nullable String description, @Nullable AuthenticatedPrincipal changedBy) {
        Objects.requireNonNull(encryptedValue, "encryptedValue");
        Optional<ConfigEntry> existing = entryRepository.findByNamespaceAndKey(namespace, key);
        if (existing.isPresent()) {
            ConfigEntry entry = existing.get();
            long newVersion = entry.version() + 1;
            entryRepository.update(entry.id(), null, encryptedValue, entry.version(), newVersion);
            recordHistory(entry, entry.encryptedValue(), encryptedValue, entry.version(), newVersion, changedBy);
        } else {
            UUID id = UUID.randomUUID();
            ConfigEntry entry = new ConfigEntry(id, namespace, key, null, valueType, true,
                    encryptedValue, description, 0);
            entryRepository.save(entry);
            recordHistory(entry, null, encryptedValue, null, 0L, changedBy);
        }
    }

    // ---- Read (cached) ----

    /**
     * Get the raw string value of a non-secret config key. Returns empty if not found or secret.
     */
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

    @Transactional(readOnly = true)
    public Optional<String> getEncryptedSecret(String namespace, String key) {
        return getEntry(namespace, key).filter(ConfigEntry::secret).map(ConfigEntry::encryptedValue);
    }

    @Transactional(readOnly = true)
    public List<ConfigEntry> getByNamespace(String namespace) {
        return entryRepository.findByNamespace(namespace);
    }

    @Transactional(readOnly = true)
    public List<ConfigHistory> getHistory(String namespace, String key) {
        return entryRepository.findByNamespaceAndKey(namespace, key)
                .map(entry -> historyRepository.findByEntryId(entry.id()))
                .orElse(List.of());
    }

    // ---- Internal ----

    private void recordHistory(
            ConfigEntry entry, @Nullable String oldValue, @Nullable String newValue,
            @Nullable Long oldVersion, @Nullable Long newVersion,
            @Nullable AuthenticatedPrincipal changedBy) {
        historyRepository.insert(new ConfigHistory(
                UUID.randomUUID(),
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
