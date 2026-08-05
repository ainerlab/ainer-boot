package dev.ainer.spring.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AinerRuntimeConfigurationMetadataTest {

    @Test
    void generatesSpringBootConfigurationMetadata() throws IOException {
        try (InputStream metadata = AinerRuntimeProperties.class.getClassLoader()
                .getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
            assertThat(metadata).isNotNull();
            assertThat(new String(metadata.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("\"name\": \"ainer.runtime.mode\"");
        }
    }
}
