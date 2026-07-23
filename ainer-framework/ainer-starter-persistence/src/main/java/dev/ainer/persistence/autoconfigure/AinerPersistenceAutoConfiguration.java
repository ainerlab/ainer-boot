package dev.ainer.persistence.autoconfigure;

import dev.ainer.persistence.mybatis.UuidTypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

@AutoConfiguration
@ConditionalOnClass(ConfigurationCustomizer.class)
public class AinerPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ainerUuidTypeHandlerCustomizer")
    public ConfigurationCustomizer ainerUuidTypeHandlerCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(UUID.class, UuidTypeHandler.class);
    }
}
