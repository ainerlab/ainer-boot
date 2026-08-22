package dev.ainer.authorization.application;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuditLevel;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationOutcome;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ReasonCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.ResourceType;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 决策审计的 {@code AuditLevel} 过滤契约（ADR-0037 §12.4）：NONE 权限不写决策行，
 * 其余级别与未注册权限码照常记录（fail-safe）。
 */
class AuthorizationDecisionAuditServiceTest {

    private final List<AuthorizationDecisionAudit> inserted = new ArrayList<>();
    private final AuthorizationDecisionAuditRepository repository = inserted::add;
    private final Clock clock = Clock.systemUTC();

    private AuthorizationDecisionAuditService serviceWith(PermissionRegistry registry) {
        return new AuthorizationDecisionAuditService(repository, clock, registry);
    }

    private AuthorizationRequest requestFor(String permissionCode) {
        return new AuthorizationRequest(
                new Requester.Authenticated(
                        new SubjectRef("https://auth.ainer.test", "user-1", SubjectType.USER),
                        Set.of("doc.write"), Set.of("ainer-api"), "audit-test"),
                AccessMode.AUTHENTICATED,
                new PermissionCode(permissionCode),
                new ResourceRef(null, new ResourceType("request"), UUID.randomUUID()),
                new AuthorizationContext(
                        Instant.now(), AuthorizationContext.Assurance.NONE,
                        "audit-test", "req-audit", null));
    }

    private AuthorizationDecision deny() {
        return AuthorizationDecision.deny(
                new ReasonCode("BINDING_REQUIRED"), "audit-test", Instant.now());
    }

    @Test
    void noneAuditLevelSuppressesDecisionRow() {
        PermissionRegistry registry = new PermissionRegistry()
                .register(new Permission(new PermissionCode("batch.read"), "read",
                        new ResourceType("request"), RiskTier.LOW,
                        AuditLevel.NONE, false, false));
        serviceWith(registry).recordIfApplicable(
                requestFor("batch.read"), deny(), "req-1", null);
        assertThat(inserted).isEmpty();
    }

    @Test
    void onDecisionLevelRecordsDecisionRow() {
        PermissionRegistry registry = new PermissionRegistry()
                .register(new Permission(new PermissionCode("doc.write"), "write",
                        new ResourceType("request"), RiskTier.MEDIUM,
                        AuditLevel.ON_DECISION, false, false));
        serviceWith(registry).recordIfApplicable(
                requestFor("doc.write"), deny(), "req-1", null);
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).outcome()).isEqualTo(AuthorizationOutcome.DENY);
        assertThat(inserted.get(0).permissionCode()).isEqualTo("doc.write");
    }

    @Test
    void unregisteredPermissionStillRecordsFailSafe() {
        // 未注册权限码照常记录——审计缺失比审计冗余更危险
        serviceWith(new PermissionRegistry()).recordIfApplicable(
                requestFor("unknown.permission"), deny(), "req-1", null);
        assertThat(inserted).hasSize(1);
    }
}
