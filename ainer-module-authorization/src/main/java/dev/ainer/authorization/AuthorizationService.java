package dev.ainer.authorization;

import dev.ainer.authorization.catalog.PermissionRegistry;
import dev.ainer.authorization.domain.AccessMode;
import dev.ainer.authorization.domain.AuthorizationContext;
import dev.ainer.authorization.domain.AuthorizationDecision;
import dev.ainer.authorization.domain.AuthorizationRequest;
import dev.ainer.authorization.domain.GrantPath;
import dev.ainer.authorization.domain.Permission;
import dev.ainer.authorization.domain.PermissionCode;
import dev.ainer.authorization.domain.ReasonCode;
import dev.ainer.authorization.domain.Requester;
import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.RiskTier;
import dev.ainer.authorization.domain.Scope;
import dev.ainer.authorization.domain.SubjectType;
import dev.ainer.authorization.policy.BindingResolver;
import dev.ainer.authorization.policy.DomainAuthorizationPolicy;
import dev.ainer.authorization.policy.PublicAccessPolicy;
import dev.ainer.authorization.policy.ScopePermissionCeiling;

import java.util.Objects;

/**
 * Pure authorization decision evaluator (ADR-0030 §6). Implements the grant-path truth table with:
 * <ul>
 *   <li>Default deny, no PUBLIC→AUTHENTICATED or AUTHENTICATED→PUBLIC fall-back.
 *   <li>Resource-type check: {@code Permission.resourceType} must match {@code ResourceRef.resourceType}.
 *   <li>System-only enforcement: {@code systemOnly} permissions require a SERVICE subject.
 *   <li>Binding ownership defense: resolver-returned bindings must belong to the requesting subject.
 *   <li>GLOBAL scope restricted to SERVICE subjects.
 *   <li>Grant ∩ state intersection: BINDING_REQUIRED = binding ∧ state; RELATION_DERIVED = relation ∧ state;
 *       BINDING_OR_RELATION = (binding ∨ relation) ∧ state.
 *   <li>Risk收口 applied uniformly (including PUBLIC path): HIGH risk without recent strong auth → CHALLENGE.
 * </ul>
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
            return AuthorizationDecision.deny(
                    AuthorizationReasonCodes.UNEXPECTED, policyVersion, request.context().evaluatedAt());
        }
    }

    private AuthorizationDecision decide(AuthorizationRequest request) {
        Permission permission = permissionRegistry.find(request.permission()).orElse(null);
        if (permission == null) {
            return deny(request, AuthorizationReasonCodes.UNKNOWN_PERMISSION);
        }

        if (!permission.resourceType().equals(request.resource().resourceType())) {
            return deny(request, AuthorizationReasonCodes.RESOURCE_TYPE_MISMATCH);
        }

        if (request.accessMode() == AccessMode.PUBLIC_PROJECTION) {
            return decidePublic(request, permission);
        }
        return decideAuthenticated(request, permission);
    }

    private AuthorizationDecision decidePublic(AuthorizationRequest request, Permission permission) {
        var projection = publicAccessPolicy.evaluate(request.permission(), request.resource());
        if (projection.isEmpty()) {
            return deny(request, AuthorizationReasonCodes.NO_PUBLIC_POLICY);
        }
        if (permission.riskTier() == RiskTier.HIGH
                && request.context().assurance() != AuthorizationContext.Assurance.RECENT_STRONG) {
            return AuthorizationDecision.challenge(
                    AuthorizationReasonCodes.STRONG_AUTH_REQUIRED, policyVersion, request.context().evaluatedAt());
        }
        return AuthorizationDecision.allowPublic(
                AuthorizationReasonCodes.PUBLIC_ALLOWED, policyVersion,
                request.context().evaluatedAt(), projection.get());
    }

    private AuthorizationDecision decideAuthenticated(AuthorizationRequest request, Permission permission) {
        if (!(request.requester() instanceof Requester.Authenticated subject)) {
            return deny(request, AuthorizationReasonCodes.AUTHENTICATED_REQUIRED);
        }

        if (permission.systemOnly() && subject.subjectRef().type() != SubjectType.SERVICE) {
            return deny(request, AuthorizationReasonCodes.SYSTEM_ONLY);
        }

        boolean scopeOk = subject.scopeCeiling().stream()
                .anyMatch(scope -> scopeCeiling.permits(scope, request.permission()));
        if (!scopeOk) {
            return deny(request, AuthorizationReasonCodes.SCOPE_CEILING);
        }

        if (subject.credentialTenantId() != null
                && request.resource().authoritativeTenantId() != null
                && !subject.credentialTenantId().equals(request.resource().authoritativeTenantId())) {
            return deny(request, AuthorizationReasonCodes.TENANT_CEILING);
        }

        GrantPath path = domainPolicy.pathFor(request.permission());

        boolean bindingGrant = bindingResolver.liveBindings(subject.subjectRef()).stream()
                .filter(b -> b.subject().equals(subject.subjectRef()))
                .filter(b -> !(b.scope() instanceof Scope.Global)
                        || subject.subjectRef().type() == SubjectType.SERVICE)
                .anyMatch(b -> b.isLive(request.permission(), request.resource(), request.context().evaluatedAt()));

        boolean relationGrant = domainPolicy.relationGrants(
                subject, request.permission(), request.resource(), request.context());

        boolean stateOk = domainPolicy.resourceStateSatisfies(
                subject, request.permission(), request.resource(), request.context());

        boolean granted = switch (path) {
            case BINDING_REQUIRED -> bindingGrant && stateOk;
            case RELATION_DERIVED -> relationGrant && stateOk;
            case BINDING_OR_RELATION -> (bindingGrant || relationGrant) && stateOk;
        };

        if (!granted) {
            return deny(request, path == GrantPath.RELATION_DERIVED
                    ? AuthorizationReasonCodes.NO_RELATION
                    : !stateOk ? AuthorizationReasonCodes.STATE_DENIED
                    : AuthorizationReasonCodes.NO_BINDING);
        }

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
