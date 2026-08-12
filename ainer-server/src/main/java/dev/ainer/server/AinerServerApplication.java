package dev.ainer.server;

import dev.ainer.authorization.AuthorizationModuleConfiguration;
import dev.ainer.module.ai.AiRuntimeModuleConfiguration;
import dev.ainer.module.config.ConfigModuleConfiguration;
import dev.ainer.module.dictionary.DictionaryModuleConfiguration;
import dev.ainer.module.notification.NotificationModuleConfiguration;
import dev.ainer.module.workspace.WorkspaceModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({WorkspaceModuleConfiguration.class, AiRuntimeModuleConfiguration.class,
        AuthorizationModuleConfiguration.class,
        DictionaryModuleConfiguration.class,
        ConfigModuleConfiguration.class,
        NotificationModuleConfiguration.class})
public class AinerServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AinerServerApplication.class, args);
    }
}
