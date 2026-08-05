package dev.ainer.app.offstate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 scaffold-ready smoke: the Ainer web starter must compose and start an application context with no
 * external services (no Docker, no database, no identity, no network). This guards the published framework
 * against auto-configuration regressions that would only surface at consumer boot time.
 */
@SpringBootTest(classes = OffStateApplication.class)
class OffStateApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextBootstrapsOffline() {
        assertThat(context).isNotNull();
        assertThat(context.containsBean("offStateApplication")).isTrue();
    }
}
