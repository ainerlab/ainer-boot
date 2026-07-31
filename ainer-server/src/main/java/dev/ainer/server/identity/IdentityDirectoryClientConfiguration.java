package dev.ainer.server.identity;

import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityDirectory;
import dev.ainer.security.client.ClientCredentialsServiceTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityDirectoryClientProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.identity.directory-client",
        name = "enabled",
        havingValue = "true")
public class IdentityDirectoryClientConfiguration {

    @Bean
    ClientCredentialsServiceTokenProvider identityDirectoryServiceTokenProvider(
            IdentityDirectoryClientProperties properties) {
        return new ClientCredentialsServiceTokenProvider(
                requireUri(properties.getTokenUri(), properties.isAllowInsecureHttp(), "token-uri"),
                properties.getClientId(),
                properties.getClientSecret(),
                Set.of(requireText(properties.getScope(), "scope")),
                properties.isAllowInsecureHttp());
    }

    @Bean
    WorkspaceIdentityDirectory workspaceIdentityDirectory(
            IdentityDirectoryClientProperties properties,
            ClientCredentialsServiceTokenProvider tokenProvider,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        URI baseUri = requireUri(properties.getBaseUrl(), properties.isAllowInsecureHttp(), "base-url");
        RestClient restClient = restClientBuilder
                .baseUrl(withoutTrailingSlash(baseUri.toString()))
                .build();
        return new HttpWorkspaceIdentityDirectory(restClient, tokenProvider, objectMapper);
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private URI requireUri(String value, boolean allowInsecureHttp, String name) {
        try {
            URI uri = URI.create(requireText(value, name));
            boolean validScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || (allowInsecureHttp && "http".equalsIgnoreCase(uri.getScheme()));
            if (!validScheme || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Ainer identity directory " + name + " is invalid", exception);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Ainer identity directory " + name + " is required");
        }
        return value.trim();
    }
}
