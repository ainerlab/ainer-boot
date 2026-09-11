package dev.ainer.module.config.config.application;

import dev.ainer.module.config.config.domain.ConfigEntry;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 配置实体的缓存读取组件（ADR-0039）。
 *
 * <p><strong>为什么单独存在</strong>：{@code @Cacheable} 只在<strong>经过 Spring 代理</strong>的调用上
 * 生效。该注解原先落在 {@link ConfigApplicationService#getEntry} 上，而 {@code getEntry} 只被同类的
 * {@code getValue}/{@code getTyped}/{@code getSecret} 自调用——自调用不经过代理，于是
 * 「注解声明了缓存，主读路径实际每次都打数据库」。把缓存读独立成组件后，配置模块的所有读路径
 * 一律经本类读取，缓存对主读路径真实生效（由集成测试用计数仓储断言：同一键第二次读取不再访问数据库）。
 *
 * <p>写入路径（{@link ConfigApplicationService#setValue}、
 * {@link ConfigApplicationService#setSecret}）故意<strong>不</strong>走本组件：写前必须读数据库当前
 * 版本做乐观锁判定，不能被缓存挡住；写成功后由服务上的 {@code @CacheEvict} 失效对应键。
 *
 * <p>包内可见：这是模块内部的读路径实现细节，不是对外 API。
 */
@Component
class ConfigEntryLookup {

    private final ConfigEntryRepository entryRepository;

    ConfigEntryLookup(ConfigEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    /**
     * 读取配置实体，命中缓存时不访问数据库。
     *
     * <p>{@code unless} 只判断 {@code #result == null}：Spring Cache 在写入前会拆包
     * {@code Optional}（{@code CacheAspectSupport#unwrapReturnValue}），SpEL 里的 {@code #result}
     * 是包内的 {@link ConfigEntry}（空 Optional 对应 {@code null}），对它调用 {@code isPresent()}
     * 会直接抛 {@code SpelEvaluationException}；命中缓存时 Spring 再按返回类型包回
     * {@code Optional}（{@code CacheAspectSupport#wrapCacheValue}）。空结果不缓存。
     */
    @Cacheable(value = ConfigApplicationService.CACHE_CONFIG_ENTRY,
            key = "#namespace + ':' + #key", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<ConfigEntry> find(String namespace, String key) {
        return entryRepository.findByNamespaceAndKey(namespace, key);
    }
}
