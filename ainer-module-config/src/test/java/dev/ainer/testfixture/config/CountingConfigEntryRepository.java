package dev.ainer.testfixture.config;

import dev.ainer.module.config.config.application.ConfigEntryRepository;
import dev.ainer.module.config.config.domain.ConfigEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 计数仓储替身（测试专用，不使用 Mockito）：委托真实 {@code ConfigEntryRepository} 实现，
 * 只统计读方法 {@link #findByNamespaceAndKey} 的调用次数。
 *
 * <p>用途：证明配置模块的主读路径（{@code getValue}/{@code getSecret}/{@code getEntry}）
 * 在缓存命中时<strong>不再访问数据库</strong>——即 {@code @Cacheable} 没有被自调用绕过。
 */
public final class CountingConfigEntryRepository implements ConfigEntryRepository {

    private final ConfigEntryRepository delegate;
    private final AtomicInteger findByNamespaceAndKeyCalls = new AtomicInteger();

    public CountingConfigEntryRepository(ConfigEntryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public UUID save(ConfigEntry entry) {
        return this.delegate.save(entry);
    }

    @Override
    public Optional<ConfigEntry> findByNamespaceAndKey(String namespace, String key) {
        this.findByNamespaceAndKeyCalls.incrementAndGet();
        return this.delegate.findByNamespaceAndKey(namespace, key);
    }

    @Override
    public List<ConfigEntry> findByNamespace(String namespace) {
        return this.delegate.findByNamespace(namespace);
    }

    @Override
    public boolean update(UUID id, String value, String encryptedValue, long expectedVersion, long newVersion) {
        return this.delegate.update(id, value, encryptedValue, expectedVersion, newVersion);
    }

    /** 归零计数器（写路径本身也会读库，断言前先归零）。 */
    public void resetFindCalls() {
        this.findByNamespaceAndKeyCalls.set(0);
    }

    /** 自上次归零以来 {@link #findByNamespaceAndKey} 的调用次数。 */
    public int findCalls() {
        return this.findByNamespaceAndKeyCalls.get();
    }
}
