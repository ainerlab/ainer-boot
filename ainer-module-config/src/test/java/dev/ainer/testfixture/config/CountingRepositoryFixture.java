package dev.ainer.testfixture.config;

import dev.ainer.module.config.config.application.ConfigEntryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 计数仓储替身的测试装配：用 {@link CountingConfigEntryRepository} 以 {@code @Primary} 覆盖
 * 真实 MyBatis 仓储，供测试断言「缓存命中时不再访问数据库」。
 *
 * <p><strong>为什么不在 {@code dev.ainer.module.config} 包内</strong>：本模块的
 * {@code ConfigModuleConfiguration} 用 {@code @ComponentScan} 扫描
 * {@code dev.ainer.module.config}（测试类路径同样在扫描范围内），包内嵌套的
 * {@code @TestConfiguration} 会被扫进<strong>每一个</strong>上下文——两个测试类各放一份就会撞
 * bean 名而启动失败。放在扫描根之外的独立测试 fixture 包里，由需要它的测试在自己的
 * {@code @SpringBootTest(classes = {...})} 中显式引用。
 */
@TestConfiguration(proxyBeanMethods = false)
public class CountingRepositoryFixture {

    @Bean
    @Primary
    public CountingConfigEntryRepository countingConfigEntryRepository(
            @Qualifier("mybatisConfigEntryRepository") ConfigEntryRepository delegate) {
        return new CountingConfigEntryRepository(delegate);
    }
}
