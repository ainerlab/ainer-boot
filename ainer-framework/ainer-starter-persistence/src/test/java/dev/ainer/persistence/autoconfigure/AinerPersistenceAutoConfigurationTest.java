package dev.ainer.persistence.autoconfigure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AinerPersistenceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AinerPersistenceAutoConfiguration.class,
                    MybatisPlusInnerInterceptorAutoConfiguration.class));

    @Test
    void registersPostgresqlUuidTypeHandler() {
        contextRunner.run(context -> {
            MybatisConfiguration configuration = new MybatisConfiguration();
            context.getBean(ConfigurationCustomizer.class).customize(configuration);

            assertThat(configuration.getTypeHandlerRegistry().hasTypeHandler(UUID.class)).isTrue();
        });
    }

    @Test
    void usesDatabaseGeneratedIdsAndBoundedPostgresqlPagination() {
        contextRunner.run(context -> {
            MybatisPlusProperties properties = new MybatisPlusProperties();
            context.getBean(MybatisPlusPropertiesCustomizer.class).customize(properties);

            assertThat(properties.getGlobalConfig().getDbConfig().getIdType()).isEqualTo(IdType.AUTO);
            assertThat(properties.getGlobalConfig().isBanner()).isFalse();
            assertThat(context).hasSingleBean(PaginationInnerInterceptor.class);
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            assertThat(context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                    .singleElement()
                    .isInstanceOfSatisfying(PaginationInnerInterceptor.class,
                            pagination -> assertThat(pagination.getMaxLimit()).isEqualTo(100L));
        });
    }

    @Test
    void keepsConsumerInterceptorsAheadOfPagination() {
        contextRunner
                .withUserConfiguration(ConsumerInterceptorConfiguration.class)
                .run(context -> assertThat(
                        context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                        .containsExactly(
                                context.getBean("consumerInnerInterceptor", InnerInterceptor.class),
                                context.getBean(PaginationInnerInterceptor.class)));
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerInterceptorConfiguration {

        @Bean
        InnerInterceptor consumerInnerInterceptor() {
            return new InnerInterceptor() {
            };
        }
    }
}
