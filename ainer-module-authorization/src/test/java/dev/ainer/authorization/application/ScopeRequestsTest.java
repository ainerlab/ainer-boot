package dev.ainer.authorization.application;

import dev.ainer.authorization.domain.Scope;
import dev.ainer.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** HTTP scope 解析的安全不变量：保留 resourceType 不得经 API 声明（防伪造绑定绕过）。 */
class ScopeRequestsTest {

    private static final UUID WORKSPACE_ID =
            UUID.fromString("019c7000-0000-7000-8000-000000000001");

    @Test
    void globalScopeParses() {
        assertThat(ScopeRequests.buildScope("GLOBAL", null, null, null))
                .isInstanceOf(Scope.Global.class);
    }

    @Test
    void workspaceScopeRequiresWorkspaceId() {
        BusinessException thrown = catchThrowableOfType(
                () -> ScopeRequests.buildScope("WORKSPACE", null, null, null),
                BusinessException.class);
        assertThat(thrown.errorCode()).isEqualTo(AuthorizationErrorCode.INVALID_SCOPE);
    }

    @Test
    void resourceScopeRejectsReservedResourceTypes() {
        // 合成锚点类型不得经公开 API 声明
        for (String reserved : new String[]{"workspace.anchor", "request"}) {
            BusinessException thrown = catchThrowableOfType(
                    () -> ScopeRequests.buildScope(
                            "RESOURCE", WORKSPACE_ID, reserved, UUID.randomUUID()),
                    BusinessException.class);
            assertThat(thrown.errorCode()).isEqualTo(AuthorizationErrorCode.INVALID_SCOPE);
        }
    }

    @Test
    void resourceScopeAcceptsOrdinaryResourceType() {
        assertThat(ScopeRequests.buildScope(
                        "RESOURCE", WORKSPACE_ID, "report", UUID.randomUUID()))
                .isInstanceOf(Scope.Resource.class);
    }

    @Test
    void unknownScopeKindIsRejected() {
        BusinessException thrown = catchThrowableOfType(
                () -> ScopeRequests.buildScope("TENANT", WORKSPACE_ID, null, null),
                BusinessException.class);
        assertThat(thrown.errorCode()).isEqualTo(AuthorizationErrorCode.INVALID_SCOPE);
    }
}
