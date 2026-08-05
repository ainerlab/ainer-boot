package dev.ainer.app.offstate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Ainer application used as the P1 scaffold-ready off-state smoke: it wires {@code ainer-starter-web}
 * and boots with no database, identity, AI or external runtime. Its only purpose is to prove that the
 * published framework + web starter compose and start an application context offline; it intentionally
 * carries no business endpoints.
 */
@SpringBootApplication
public class OffStateApplication {

    public static void main(String[] args) {
        SpringApplication.run(OffStateApplication.class, args);
    }
}
