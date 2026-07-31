package dev.ainer.server.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.workspace.workspace.application.WorkspaceErrorCode;
import dev.ainer.module.workspace.workspace.application.WorkspaceIdentityDirectory;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.security.client.ClientCredentialsServiceTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

final class HttpWorkspaceIdentityDirectory implements WorkspaceIdentityDirectory {

    private final RestClient restClient;
    private final ClientCredentialsServiceTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;

    HttpWorkspaceIdentityDirectory(
            RestClient restClient,
            ClientCredentialsServiceTokenProvider tokenProvider,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isActiveMember(TenantId tenantId, SubjectId subjectId) {
        UUID expectedTenant;
        UUID expectedSubject;
        try {
            expectedTenant = UUID.fromString(tenantId.value());
            expectedSubject = UUID.fromString(subjectId.value());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        try {
            String body = restClient.get()
                    .uri("/internal/identity/directory/tenants/{tenantId}/members/{subjectId}",
                            expectedTenant, expectedSubject)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.accessToken())
                    .retrieve()
                    .body(String.class);
            JsonNode data = objectMapper.readTree(body).path("data");
            return expectedTenant.toString().equals(data.path("tenantId").stringValue())
                    && expectedSubject.toString().equals(data.path("subjectId").stringValue());
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        } catch (RuntimeException exception) {
            throw new BusinessException(WorkspaceErrorCode.IDENTITY_DIRECTORY_UNAVAILABLE);
        }
    }
}
