package dev.ainer.cache.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheAspectSupport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 真正打开 Spring Cache 的自动配置（ADR-0039 落地补齐）。
 *
 * <p>背景：{@code @Cacheable}/{@code @CacheEvict} 早已在字典与配置模块使用，但全仓没有任何
 * 生产代码声明 {@code @EnableCaching}；Spring Boot 也不会替产品打开缓存
 * （{@code CacheAutoConfiguration} 自身以 {@code @ConditionalOnBean(CacheAspectSupport.class)} 为前提），
 * 结果是这些注解<strong>全部是死注解</strong>：方法每次都真实执行，缓存从未生效且没有任何提示。
 *
 * <p>装配规则：
 * <ul>
 *   <li>{@code ainer.cache.enabled} 缺失或为 {@code true} 时启用；为 {@code false} 时不注册切面，
 *       注解直接落到方法体（显式关闭，而不是"以为开着"）；</li>
 *   <li>产品自己声明 {@code @EnableCaching} 时（已存在 {@link CacheAspectSupport}）让位，
 *       不重复注册缓存切面。</li>
 * </ul>
 *
 * <p>本类同时负责绑定 {@link AinerCacheProperties}，因此 {@code ainer.cache.*} 的非法值
 * 会在启动期以绑定错误暴露（{@link AinerCacheLockAutoConfiguration} 也会绑定同一属性，
 * 即使本配置被让位或关闭，属性校验依然生效）。
 */
@AutoConfiguration
@EnableConfigurationProperties(AinerCacheProperties.class)
@EnableCaching
@ConditionalOnMissingBean(CacheAspectSupport.class)
@ConditionalOnProperty(prefix = "ainer.cache", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AinerCacheAutoConfiguration {
}
