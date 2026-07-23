package dev.ainer.spring.runtime;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(AinerRuntimeProperties.class)
public class AinerRuntimeAutoConfiguration {
}
