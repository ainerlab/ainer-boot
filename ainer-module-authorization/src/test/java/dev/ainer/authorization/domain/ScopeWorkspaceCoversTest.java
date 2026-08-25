package dev.ainer.authorization.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeWorkspaceCoversTest {

    private static final UUID WORKSPACE = UUID.fromString("019c7000-0000-7000-8000-000000000001");

    @Test
    void workspaceScopeMatchesSameWorkspace() {
        ResourceRef resource = new ResourceRef(WORKSPACE, new ResourceType("request"), WORKSPACE);
        assertThat(new Scope.Workspace(WORKSPACE).covers(resource)).isTrue();
        assertThat(new Scope.Workspace(UUID.fromString("019c7000-0000-7000-8000-000000000099"))
                .covers(resource)).isFalse();
    }

    @Test
    void unscopedRequestResourceIsCoveredByAnyWorkspaceBinding() {
        ResourceRef resource = new ResourceRef(null, new ResourceType("request"), WORKSPACE);
        assertThat(new Scope.Workspace(WORKSPACE).covers(resource)).isTrue();
    }

    @Test
    void unscopedNonRequestResourceIsNotCovered() {
        ResourceRef resource = new ResourceRef(null, new ResourceType("workspace"), WORKSPACE);
        assertThat(new Scope.Workspace(WORKSPACE).covers(resource)).isFalse();
    }
}
