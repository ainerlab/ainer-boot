package dev.ainer.module.config.config.application;

import dev.ainer.module.config.config.domain.ConfigEntry;
import dev.ainer.module.config.config.domain.ConfigHistory;
import dev.ainer.module.config.config.domain.ConfigValueType;
import dev.ainer.security.token.AuthenticatedPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for dynamic configuration (ADR-0038). Provides type-safe value retrieval,
 * hot-reload cache (invalidated on write), version history for every change, and secret encryption
 * delegation.
 *
 * <p>Secret values are never stored in plaintext. The caller supplies the plaintext when setting a
 * secret; the service stores the encrypted ciphertext. Retrieval of a secret returns the ciphertext
 * — decryption is the caller's responsibility (product supplies the encryption adapter).
 */
@Service
@Transactional
public class ConfigApplicationService {

    private final ConfigEntryRepository entryRepository;
    private final ConfigHistoryRepository historyRepository;
    private final Clock clock;

    /** Hot-reload cache: namespace:key → current entry. Invalidated on any write. */
    private final Map<String, ConfigEntry> cache = new ConcurrentHashMap<>();

    public ConfigApplicationService(
            ConfigEntryRepository entryRepository,
            ConfigHistoryRepository historyRepository,
            Clock clock) {
        this.entryRepository = entryRepository;
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    // ---- Write ----

    /**
     * Create or update a non-secret config value. Records a history entry on every change.
     */
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
        invalidateCache();
    }

    /**
     * Create or update a secret config value. The {@code encryptedValue} is the AES-GCM ciphertext;
     * the plaintext is never persisted.
     */
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
        invalidateCache();
    }

    // ---- Read (cached) ----

    /**
     * Get the raw string value of a non-secret config key. Returns empty if not found or if the key
     * is a secret (use {@link #getEncryptedSecret} for secrets).
     */
    @Transactional(readOnly = true)
    public Optional<String> getValue(String namespace, String key) {
        return getEntry(namespace, key).filter(e -> !e.secret()).map(ConfigEntry::value);
    }

    /**
     * Get a typed value with automatic parsing. Throws on type mismatch.
     */
    @Transactional(readOnly = true)
    public <T> Optional<T> getTyped(String namespace, String key, Class<T> type) {
        return getValue(namespace, key).map(raw -> parseType(raw, type));
    }

    /**
     * Get the encrypted ciphertext of a secret config key. Decryption is the caller's responsibility.
     */
    @Transactional(readOnly = true)
    public Optional<String> getEncryptedSecret(String namespace, String key) {
        return getEntry(namespace, key).filter(ConfigEntry::secret).map(ConfigEntry::encryptedValue);
    }

    /**
     * Get all config entries in a namespace.
     */
    @Transactional(readOnly = true)
    public List<ConfigEntry> getByNamespace(String namespace) {
        return entryRepository.findByNamespace(namespace);
    }

    /**
     * Get the version history of a config key.
     */
    @Transactional(readOnly = true)
    public List<ConfigHistory> getHistory(String namespace, String key) {
        return entryRepository.findByNamespaceAndKey(namespace, key)
                .map(entry -> historyRepository.findByEntryId(entry.id()))
                .orElse(List.of());
    }

    // ---- Internal ----

    private Optional<ConfigEntry> getEntry(String namespace, String key) {
        String cacheKey = namespace + ":" + key;
        return Optional.ofNullable(cache.computeIfAbsent(cacheKey, k ->
                entryRepository.findByNamespaceAndKey(namespace, key).orElse(null)));
    }

    private void invalidateCache() {
        cache.clear();
    }

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
        if (type == String.class) {
            return (T) raw;
        }
        if (type == Integer.class || type == int.class) {
            return (T) Integer.valueOf(raw);
        }
        if (type == Long.class || type == long.class) {
            return (T) Long.valueOf(raw);
        }
        if (type == Boolean.class || type == boolean.class) {
            return (T) Boolean.valueOf(raw);
        }
        if (type == java.math.BigDecimal.class) {
            return (T) new java.math.BigDecimal(raw);
        }
        throw new IllegalArgumentException("Unsupported config type: " + type);
    }
}
