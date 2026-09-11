package dev.ainer.cache.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Locale;

/**
 * ADR-0039 缓存 starter 的装配条件集合。
 *
 * <p>为什么不用 {@code @ConditionalOnProperty}：它的 {@code havingValue} 是<strong>大小写敏感</strong>
 * 的裸字符串比较，而 {@code ainer.cache.type}/{@code ainer.cache.lock.type} 绑定的是枚举，
 * 绑定本身大小写不敏感。若产品写 {@code ainer.cache.type=REDIS}（枚举的规范拼写），
 * {@code havingValue="redis"} 不会命中，结果是「枚举绑定成功但后端自动配置不激活」——
 * 上下文里没有任何 {@code CacheManager}，缓存切面在启动尾期才报一句难以定位的错误。
 * 这里改为读取属性后按枚举解析（忽略大小写），非法值不匹配任何后端，交由
 * {@code @ConfigurationProperties} 绑定阶段报出明确错误。
 */
public final class AinerCacheConditions {

    /** 缓存后端类型属性名。 */
    static final String CACHE_TYPE_PROPERTY = "ainer.cache.type";

    /** 分布式锁选择属性名。 */
    static final String LOCK_TYPE_PROPERTY = "ainer.cache.lock.type";

    /** 本地 Caffeine 后端装配条件（{@code type} 缺失或为 {@code LOCAL}）。 */
    public static final class OnCacheTypeLocal extends OnEnumProperty<AinerCacheProperties.CacheType> {

        public OnCacheTypeLocal() {
            super(CACHE_TYPE_PROPERTY, AinerCacheProperties.CacheType.class,
                    AinerCacheProperties.CacheType.LOCAL, AinerCacheProperties.CacheType.LOCAL);
        }
    }

    /** Redis 后端装配条件（{@code type=REDIS}）。 */
    public static final class OnCacheTypeRedis extends OnEnumProperty<AinerCacheProperties.CacheType> {

        public OnCacheTypeRedis() {
            super(CACHE_TYPE_PROPERTY, AinerCacheProperties.CacheType.class,
                    AinerCacheProperties.CacheType.REDIS, AinerCacheProperties.CacheType.LOCAL);
        }
    }

    /** 锁策略 {@code AUTO} 装配条件（{@code lock.type} 缺失或为 {@code AUTO}）。 */
    public static final class OnLockTypeAuto extends OnEnumProperty<AinerCacheProperties.LockType> {

        public OnLockTypeAuto() {
            super(LOCK_TYPE_PROPERTY, AinerCacheProperties.LockType.class,
                    AinerCacheProperties.LockType.AUTO, AinerCacheProperties.LockType.AUTO);
        }
    }

    private AinerCacheConditions() {
    }

    /**
     * 按枚举解析属性值后与期望值比较的基础条件。
     *
     * @param <T> 枚举类型
     */
    abstract static class OnEnumProperty<T extends Enum<T>> extends SpringBootCondition {

        private final String property;
        private final Class<T> enumType;
        private final T expected;
        private final T defaultValue;

        OnEnumProperty(String property, Class<T> enumType, T expected, T defaultValue) {
            this.property = property;
            this.enumType = enumType;
            this.expected = expected;
            this.defaultValue = defaultValue;
        }

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String raw = context.getEnvironment().getProperty(this.property);
            T actual;
            if (raw == null || raw.isBlank()) {
                actual = this.defaultValue;
            } else {
                try {
                    actual = Enum.valueOf(this.enumType, raw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    return ConditionOutcome.noMatch("无法识别的 " + this.property + "=" + raw
                            + "（可选值：" + String.join("/", names()) + "）");
                }
            }
            return actual == this.expected
                    ? ConditionOutcome.match(this.property + "=" + actual)
                    : ConditionOutcome.noMatch(this.property + "=" + actual);
        }

        private String[] names() {
            T[] values = this.enumType.getEnumConstants();
            String[] names = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                names[i] = values[i].name();
            }
            return names;
        }
    }
}
