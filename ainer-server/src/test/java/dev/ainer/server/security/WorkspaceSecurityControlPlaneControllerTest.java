package dev.ainer.server.security;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAction;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAudit;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditCursor;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditExportBatch;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationAuditLifecycleService;
import dev.ainer.module.workspace.workspace.application.WorkspaceAuthorizationDecision;
import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryRequest;
import dev.ainer.module.workspace.workspace.application.WorkspaceOwnerRecoveryService;
import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;
import dev.ainer.security.service.AuthenticatedService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceSecurityControlPlaneControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-23T07:00:00Z");
    private static final String TENANT_ID = "tenant:security-control";

    @Test
    void ownerRecoveryUsesTenantBoundServiceScopesAndIndependentSubjects() {
        StubOwnerRecoveryService service = new StubOwnerRecoveryService();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        WorkspaceOwnerRecoveryController controller = new WorkspaceOwnerRecoveryController(
                service, new WorkspaceOwnerRecoverySettings(Duration.ofMinutes(15)), meters);
        WorkspaceOwnerRecoveryRequestBody body = new WorkspaceOwnerRecoveryRequestBody(
                UUID.randomUUID(), "subject:new-owner", "INC-OWNER-100");

        var requested = controller.requestRecovery(
                TENANT_ID,
                body,
                authentication(
                        "operator:request", TENANT_ID, AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_workspace.owner-recovery.request"),
                new MockHttpServletRequest());
        var executed = controller.approve(
                TENANT_ID,
                requested.data().requestId(),
                authentication(
                        "operator:approve", TENANT_ID, AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_workspace.owner-recovery.approve"),
                new MockHttpServletRequest());

        assertThat(requested.data().status()).isEqualTo("REQUESTED");
        assertThat(executed.data().status()).isEqualTo("EXECUTED");
        assertThat(service.requester).isEqualTo("operator:request");
        assertThat(service.approver).isEqualTo("operator:approve");
        assertThat(meters.counter("ainer.workspace.owner.recovery.requested").count()).isEqualTo(1);
        assertThat(meters.counter("ainer.workspace.owner.recovery.executed").count()).isEqualTo(1);

        assertForbidden(() -> controller.requestRecovery(
                TENANT_ID,
                body,
                authentication(
                        "operator:user", TENANT_ID, "USER",
                        "SCOPE_workspace.owner-recovery.request"),
                new MockHttpServletRequest()));
        assertForbidden(() -> controller.requestRecovery(
                TENANT_ID,
                body,
                authentication(
                        "operator:cross", "tenant:other", AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_workspace.owner-recovery.request"),
                new MockHttpServletRequest()));
    }

    @Test
    void auditExportRequiresExactTrustedServiceAndUsesTenantCursor() {
        StubAuditLifecycleService service = new StubAuditLifecycleService();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        WorkspaceAuthorizationAuditExportController controller =
                new WorkspaceAuthorizationAuditExportController(
                        service,
                        new WorkspaceAuthorizationAuditExportSettings("siem:trusted"),
                        meters);

        var result = controller.export(
                TENANT_ID,
                null,
                null,
                200,
                authentication(
                        "siem:trusted", TENANT_ID, AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_workspace.audit.export"),
                new MockHttpServletRequest());

        assertThat(result.data().items()).hasSize(1);
        assertThat(result.data().nextId()).isEqualTo(service.audit.id());
        assertThat(meters.counter("ainer.workspace.authorization.audit.exported").count())
                .isEqualTo(1);

        assertForbidden(() -> controller.export(
                TENANT_ID,
                null,
                null,
                200,
                authentication(
                        "siem:unexpected", TENANT_ID, AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_workspace.audit.export"),
                new MockHttpServletRequest()));
        assertForbidden(() -> controller.export(
                TENANT_ID,
                null,
                null,
                200,
                authentication(
                        "siem:trusted", TENANT_ID, "USER",
                        "SCOPE_workspace.audit.export"),
                new MockHttpServletRequest()));
        assertThatThrownBy(() -> controller.export(
                TENANT_ID,
                NOW,
                null,
                200,
                authentication(
                        "siem:trusted", TENANT_ID, AuthenticatedService.SERVICE_ACTOR_TYPE,
                        "SCOPE_workspace.audit.export"),
                new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class);
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(StandardErrorCode.FORBIDDEN));
    }

    private Authentication authentication(
            String subject, String tenantId, String actorType, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("tenant_id", tenantId)
                .claim(AuthenticatedService.ACTOR_TYPE_CLAIM, actorType)
                .build();
        return new JwtAuthenticationToken(
                jwt,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static final class StubOwnerRecoveryService extends WorkspaceOwnerRecoveryService {
        private String requester;
        private String approver;
        private WorkspaceOwnerRecoveryRequest request;

        private StubOwnerRecoveryService() {
            super(null, null, null, null, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        public WorkspaceOwnerRecoveryRequest requestRecovery(
                String requesterServiceId,
                TenantId tenantId,
                UUID workspaceId,
                SubjectId newOwnerSubjectId,
                String incidentReference,
                Duration approvalTtl) {
            requester = requesterServiceId;
            request = new WorkspaceOwnerRecoveryRequest(
                    UUID.randomUUID(), tenantId, workspaceId, newOwnerSubjectId,
                    requester, null, incidentReference, "REQUESTED", NOW,
                    NOW.plus(approvalTtl), null);
            return request;
        }

        @Override
        public WorkspaceOwnerRecoveryRequest approveAndExecute(
                String approverServiceId, TenantId tenantId, UUID requestId) {
            approver = approverServiceId;
            return new WorkspaceOwnerRecoveryRequest(
                    request.id(), request.tenantId(), request.workspaceId(), request.newOwnerSubjectId(),
                    request.requestedBy(), approver, request.incidentReference(), "EXECUTED",
                    request.requestedAt(), request.expiresAt(), NOW);
        }
    }

    private static final class StubAuditLifecycleService
            extends WorkspaceAuthorizationAuditLifecycleService {
        private final WorkspaceAuthorizationAudit audit = new WorkspaceAuthorizationAudit(
                UUID.randomUUID(), TENANT_ID, UUID.randomUUID(), "subject:actor", null,
                WorkspaceAuthorizationAction.WORKSPACE_READ,
                WorkspaceAuthorizationDecision.ALLOWED,
                "AINER.COMMON.OK",
                NOW.minusSeconds(10));

        private StubAuditLifecycleService() {
            super(null, null, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        public WorkspaceAuthorizationAuditExportBatch export(
                String exporterServiceId,
                TenantId tenantId,
                WorkspaceAuthorizationAuditCursor cursor,
                int limit) {
            return new WorkspaceAuthorizationAuditExportBatch(
                    List.of(audit), new WorkspaceAuthorizationAuditCursor(audit.occurredAt(), audit.id()), false);
        }
    }
}
