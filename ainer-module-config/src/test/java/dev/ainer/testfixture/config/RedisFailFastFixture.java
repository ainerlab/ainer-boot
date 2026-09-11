package dev.ainer.testfixture.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * 测试专用 Lettuce 客户端策略：断连时<strong>立即失败</strong>，不把命令缓冲到重连后重放。
 *
 * <p>原因：本仓库 CI/本地环境（macOS Colima）偶发 Redis 连接抖动（日志里可见
 * {@code Connection refused}/{@code Connection reset}）。Lettuce 默认的
 * {@code DisconnectedBehavior.DEFAULT} 会在断连期间缓冲命令，重连后按队列重放——
 * 于是「先写、后 evict、再读」可能变成「先 evict、后写」落地，缓存里静默留下过期值，
 * 表现为随机出现的断言失败，比直接报错难定位得多。
 *
 * <p>这里改成 {@code REJECT_COMMANDS} + {@code autoReconnect(false)}：连接不可用时直接抛错，
 * 让连接问题以「明确的连接异常」暴露，而不是以「顺序错乱」暴露。只影响测试作用域。
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisFailFastFixture {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer failFastLettuceClientConfigurationCustomizer() {
        return builder -> builder
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(false)
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build());
    }
}
