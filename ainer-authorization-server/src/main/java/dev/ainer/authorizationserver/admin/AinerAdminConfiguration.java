package dev.ainer.authorizationserver.admin;

import dev.ainer.module.identity.account.application.IdentityApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@Profile("dev")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AinerAdminBrowserClientProperties.class,
        AinerAdminDevBootstrapProperties.class
})
public class AinerAdminConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.admin.browser-client",
            name = "enabled",
            havingValue = "true")
    AinerAdminBrowserClientBootstrapRunner ainerAdminBrowserClientBootstrapRunner(
            AinerAdminBrowserClientProperties properties,
            RegisteredClientRepository registeredClientRepository) {
        return new AinerAdminBrowserClientBootstrapRunner(properties, registeredClientRepository);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ainer.admin.dev-bootstrap",
            name = "enabled",
            havingValue = "true")
    AinerAdminDevFixtureRunner ainerAdminDevFixtureRunner(
            AinerAdminDevBootstrapProperties properties,
            IdentityApplicationService identityApplicationService) {
        return new AinerAdminDevFixtureRunner(properties, identityApplicationService);
    }
}
