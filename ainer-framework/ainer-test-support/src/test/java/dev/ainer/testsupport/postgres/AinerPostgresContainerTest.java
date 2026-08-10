package dev.ainer.testsupport.postgres;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AinerPostgresContainerTest {

    @Test
    void pinsTheAinerDatabaseBaselineImage() {
        assertThat(AinerPostgresContainer.IMAGE).isEqualTo("postgres:18.3-alpine");
    }
}