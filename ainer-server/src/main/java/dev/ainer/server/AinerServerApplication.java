package dev.ainer.server;

import dev.ainer.authorization.AuthorizationModuleConfiguration;
import dev.ainer.module.ai.AiRuntimeModuleConfiguration;
import dev.ainer.module.workspace.WorkspaceModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({WorkspaceModuleConfiguration.class, AiRuntimeModuleConfiguration.class,
        AuthorizationModuleConfiguration.class})
public class AinerServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AinerServerApplication.class, args);
    }
}
