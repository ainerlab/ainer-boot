package dev.ainer.persistence.autoconfigure;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AinerPersistenceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AinerPersistenceAutoConfiguration.class));

    @Test
    void registersPostgresqlUuidTypeHandler() {
        contextRunner.run(context -> {
            Configuration configuration = new Configuration();
            context.getBean(ConfigurationCustomizer.class).customize(configuration);

            assertThat(configuration.getTypeHandlerRegistry().hasTypeHandler(UUID.class)).isTrue();
        });
    }
}
