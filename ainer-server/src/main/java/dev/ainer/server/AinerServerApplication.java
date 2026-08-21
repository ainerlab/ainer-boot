package dev.ainer.server;

import dev.ainer.authorization.AuthorizationModuleConfiguration;
import dev.ainer.module.ai.AiRuntimeModuleConfiguration;
import dev.ainer.module.ai.agent.AiAgentModuleConfiguration;
import dev.ainer.module.config.ConfigModuleConfiguration;
import dev.ainer.module.dictionary.DictionaryModuleConfiguration;
import dev.ainer.module.file.FileModuleConfiguration;
import dev.ainer.module.knowledge.KnowledgeModuleConfiguration;
import dev.ainer.module.task.TaskModuleConfiguration;
import dev.ainer.server.authorization.AinerServerAuthorizationPolicyConfiguration;
import dev.ainer.module.organization.OrganizationModuleConfiguration;
import dev.ainer.module.notification.NotificationModuleConfiguration;
import dev.ainer.module.workspace.WorkspaceModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({AinerServerAuthorizationPolicyConfiguration.class,
        WorkspaceModuleConfiguration.class, AiRuntimeModuleConfiguration.class,
        AuthorizationModuleConfiguration.class,
        DictionaryModuleConfiguration.class,
        ConfigModuleConfiguration.class,
        NotificationModuleConfiguration.class,
        FileModuleConfiguration.class,
        OrganizationModuleConfiguration.class,
        AiAgentModuleConfiguration.class,
        KnowledgeModuleConfiguration.class,
        TaskModuleConfiguration.class,
        AinerServerAuthorizationPolicyConfiguration.class})
public class AinerServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AinerServerApplication.class, args);
    }
}
