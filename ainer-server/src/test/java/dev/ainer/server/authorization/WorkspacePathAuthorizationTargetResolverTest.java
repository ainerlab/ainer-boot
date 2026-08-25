package dev.ainer.server.authorization;

import dev.ainer.authorization.domain.ResourceRef;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspacePathAuthorizationTargetResolverTest {

    private static final UUID WORKSPACE =
            UUID.fromString("019c7000-0000-7000-8000-000000000001");

    private final WorkspacePathAuthorizationTargetResolver resolver =
            new WorkspacePathAuthorizationTargetResolver();

    @Test
    void extractsWorkspaceIdFromMvcPathVariableNamedId() {
        MockHttpServletRequest request = workspaceRequest("/api/workspaces/" + WORKSPACE);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("id", WORKSPACE.toString()));

        Optional<ResourceRef> resolved = resolver.resolve(request, "workspace.read");

        assertThat(resolved).isPresent();
        assertThat(resolved.get().workspaceId()).isEqualTo(WORKSPACE);
        assertThat(resolved.get().resourceType())
                .isEqualTo(AinerServerAuthorizationPolicyConfiguration.REQUEST_RESOURCE);
        assertThat(resolved.get().resourceId()).isEqualTo(WORKSPACE);
    }

    @Test
    void extractsWorkspaceIdFromWorkspaceIdPathVariable() {
        MockHttpServletRequest request = workspaceRequest(
                "/api/workspaces/" + WORKSPACE + "/authorization-audits");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("workspaceId", WORKSPACE.toString()));

        assertThat(resolver.resolve(request, "workspace.audit.read"))
                .get()
                .extracting(ResourceRef::workspaceId)
                .isEqualTo(WORKSPACE);
    }

    @Test
    void fallsBackToServletPathWhenMvcVariablesAbsent() {
        MockHttpServletRequest request = workspaceRequest(
                "/api/workspaces/" + WORKSPACE + "/members");

        assertThat(resolver.resolve(request, "workspace.write"))
                .get()
                .extracting(ResourceRef::workspaceId)
                .isEqualTo(WORKSPACE);
    }

    @Test
    void ignoresCollectionEndpointsWithoutWorkspaceId() {
        MockHttpServletRequest request = workspaceRequest("/api/workspaces");
        request.setServletPath("/api/workspaces");

        assertThat(resolver.resolve(request, "workspace.read")).isEmpty();
        assertThat(resolver.resolve(request, "workspace.write")).isEmpty();
    }

    @Test
    void ignoresNonWorkspacePermissionsAndForeignPaths() {
        MockHttpServletRequest workspace = workspaceRequest("/api/workspaces/" + WORKSPACE);
        workspace.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("id", WORKSPACE.toString()));
        assertThat(resolver.resolve(workspace, "file.read")).isEmpty();

        MockHttpServletRequest file = new MockHttpServletRequest("GET", "/api/files/" + WORKSPACE);
        file.setServletPath("/api/files/" + WORKSPACE);
        file.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("id", WORKSPACE.toString()));
        assertThat(resolver.resolve(file, "workspace.read")).isEmpty();
    }

    @Test
    void ignoresQueryStringWorkspaceId() {
        MockHttpServletRequest request = workspaceRequest("/api/workspaces");
        request.setServletPath("/api/workspaces");
        request.setQueryString("workspaceId=" + WORKSPACE);
        request.setParameter("workspaceId", WORKSPACE.toString());

        assertThat(resolver.resolve(request, "workspace.read")).isEmpty();
    }

    @Test
    void rejectsMalformedPathVariable() {
        MockHttpServletRequest request = workspaceRequest("/api/workspaces/not-a-uuid");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("id", "not-a-uuid"));

        assertThatThrownBy(() -> resolver.resolve(request, "workspace.read"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a UUID");
    }

    private static MockHttpServletRequest workspaceRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
