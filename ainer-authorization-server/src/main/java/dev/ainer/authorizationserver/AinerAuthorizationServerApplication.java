package dev.ainer.authorizationserver;

import dev.ainer.module.identity.IdentityModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(IdentityModuleConfiguration.class)
public class AinerAuthorizationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AinerAuthorizationServerApplication.class, args);
    }
}
