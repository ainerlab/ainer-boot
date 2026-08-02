package dev.ainer.authorization;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ReasonCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.PublicAccessPolicy;
import dev.ainer.authorization.policy.RelationOutcome;
import dev.ainer.authorization.policy.ScopePermissionCeiling;

import java.util.Objects;

/**
 * Pure authorization decision evaluator (ADR-0030 §6). Implements the grant-path truth table with default
 * deny, access-mode pipeline isolation (no PUBLIC/AUTHENTICATED fall-back), OAuth scope ceiling, scope-matched
 * live {@code SubjectBinding}, relation-derived grants, tenant ceiling and HIGH-risk step-up challenge.
 *
 * <p>S0 is in-memory: {@link BindingResolver} is a test fixture here and becomes PostgreSQL-backed in S1.
 * Unknown permission, unknown policy, conflicts and exceptions always deny; an AUTHENTICATED deny never
 * falls back to the public path.
 */
public final class AuthorizationService {

    private final PermissionRegistry permissionRegistry;
    private final ScopePermissionCeiling scopeCeiling;
    private final PublicAccessPolicy publicAccessPolicy;
    private final DomainAuthorizationPolicy domainPolicy;
    private final BindingResolver bindingResolver;
    private final String policyVersion;

    public AuthorizationService(
            PermissionRegistry permissionRegistry,
            ScopePermissionCeiling scopeCeiling,
            PublicAccessPolicy publicAccessPolicy,
            DomainAuthorizationPolicy domainPolicy,
            BindingResolver bindingResolver,
            String policyVersion) {
        this.permissionRegistry = Objects.requireNonNull(permissionRegistry, "permissionRegistry");
        this.scopeCeiling = Objects.requireNonNull(scopeCeiling, "scopeCeiling");
        this.publicAccessPolicy = Objects.requireNonNull(publicAccessPolicy, "publicAccessPolicy");
        this.domainPolicy = Objects.requireNonNull(domainPolicy, "domainPolicy");
        this.bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
    }

    public AuthorizationDecision authorize(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            return decide(request);
        } catch (RuntimeException unexpected) {
            // ADR-0030: any unknown/conflicting/exceptional state denies; never ALLOW.
            return AuthorizationDecision.deny(
                    AuthorizationReasonCodes.UNEXPECTED, policyVersion, request.context().evaluatedAt());
        }
    }

    private AuthorizationDecision decide(AuthorizationRequest request) {
        Permission permission = permissionRegistry.find(request.permission()).orElse(null);
        if (permission == null) {
            return deny(request, AuthorizationReasonCodes.UNKNOWN_PERMISSION);
        }

        if (request.accessMode() == dev.ainer.authorization.domain.AccessMode.PUBLIC_PROJECTION) {
            // PUBLIC pipeline: explicit policy only; skip scope/binding/relation entirely.
            if (publicAccessPolicy.allows(request.permission(), request.resource())) {
                return allow(request, AuthorizationReasonCodes.PUBLIC_ALLOWED);
            }
            return deny(request, AuthorizationReasonCodes.NO_PUBLIC_POLICY);
        }

        // AUTHENTICATED pipeline: anonymous can never enter it.
        if (!(request.requester() instanceof Requester.Authenticated subject)) {
            return deny(request, AuthorizationReasonCodes.AUTHENTICATED_REQUIRED);
        }

        // OAuth scope ceiling (issuer/audience are resolved by the security layer, not this module).
        boolean scopeOk = subject.scopeCeiling().stream()
                .anyMatch(scope -> scopeCeiling.permits(scope, request.permission()));
        if (!scopeOk) {
            return deny(request, AuthorizationReasonCodes.SCOPE_CEILING);
        }

        // credential tenant is an uncrossable ceiling for tenant-owned resources.
        if (subject.credentialTenantId() != null
                && request.resource().authoritativeTenantId() != null
                && !subject.credentialTenantId().equals(request.resource().authoritativeTenantId())) {
            return deny(request, AuthorizationReasonCodes.TENANT_CEILING);
        }

        GrantPath path = domainPolicy.pathFor(request.permission());
        boolean bindingOk = bindingResolver.liveBindings(subject.subjectRef()).stream()
                .anyMatch(binding -> binding.isLive(request.permission(), request.resource(), request.context().evaluatedAt()));
        boolean relationOk = domainPolicy.relationAllows(
                subject, request.permission(), request.resource(), request.context()) == RelationOutcome.ALLOWED;

        boolean granted = switch (path) {
            case BINDING_REQUIRED -> bindingOk;
            case RELATION_DERIVED -> relationOk;
            case BINDING_OR_RELATION -> bindingOk || relationOk;
        };
        if (!granted) {
            return deny(request, path == GrantPath.RELATION_DERIVED
                    ? AuthorizationReasonCodes.NO_RELATION
                    : AuthorizationReasonCodes.NO_BINDING);
        }

        // HIGH-risk actions require recent strong authentication; otherwise CHALLENGE (never ALLOW).
        if (permission.riskTier() == RiskTier.HIGH
                && request.context().assurance() != AuthorizationContext.Assurance.RECENT_STRONG) {
            return AuthorizationDecision.challenge(
                    AuthorizationReasonCodes.STRONG_AUTH_REQUIRED, policyVersion, request.context().evaluatedAt());
        }
        return allow(request, AuthorizationReasonCodes.AUTHORIZED);
    }

    private AuthorizationDecision allow(AuthorizationRequest request, ReasonCode reason) {
        return AuthorizationDecision.allow(reason, policyVersion, request.context().evaluatedAt());
    }

    private AuthorizationDecision deny(AuthorizationRequest request, ReasonCode reason) {
        return AuthorizationDecision.deny(reason, policyVersion, request.context().evaluatedAt());
    }
}
