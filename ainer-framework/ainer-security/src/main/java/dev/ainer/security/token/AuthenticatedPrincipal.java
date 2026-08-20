package dev.ainer.security.token;

import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.PrincipalSubjectRef;
import dev.ainer.security.principal.ServiceSubjectRef;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * 已验证 access token 的类型化、按 token profile 限定的投影
 * （ADR-0030 §2.2、ADR-0033 Greenfield §6.1）。
 *
 * <p>这是 Foundation 代码的规范请求期主体。它把带权威限定的
 * {@link PrincipalSubjectRef} 与封闭的 {@link TokenProfile}、claim 契约版本、OAuth
 * audience 与 scope 上限、认证保障等级配对。Workspace 与隔离是资源事实，
 * 不是主体属性。
 *
 * <p>不变量在构造时强制执行：{@code USER_*} profile 必须配 {@link HumanSubjectRef}，
 * {@code SERVICE_V1} 必须配 {@link ServiceSubjectRef}。workspace 访问上限
 * （面向 {@code USER_WORKSPACE_V1}）将在后续切片随 {@code WorkspaceRef} 引入；
 * 在此之前，带 workspace 范围的主体仍可通过其 profile 与 scope 上限表达。
 */
public record AuthenticatedPrincipal(
        PrincipalSubjectRef principalSubjectRef,
        IdentityAuthorityRef authority,
        TokenProfile tokenProfile,
        String claimContractVersion,
        Set<String> audiences,
        Set<String> scopes,
        String assurance,
        String clientId,
        @Nullable Long securityEpoch) {

    public AuthenticatedPrincipal(
            PrincipalSubjectRef principalSubjectRef,
            IdentityAuthorityRef authority,
            TokenProfile tokenProfile,
            String claimContractVersion,
            Set<String> audiences,
            Set<String> scopes,
            String assurance,
            String clientId) {
        this(principalSubjectRef, authority, tokenProfile, claimContractVersion,
                audiences, scopes, assurance, clientId, null);
    }

    public AuthenticatedPrincipal {
        Objects.requireNonNull(principalSubjectRef, "principalSubjectRef");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(tokenProfile, "tokenProfile");
        Objects.requireNonNull(claimContractVersion, "claimContractVersion");
        Objects.requireNonNull(audiences, "audiences");
        Objects.requireNonNull(scopes, "scopes");
        Objects.requireNonNull(assurance, "assurance");
        if (claimContractVersion.isBlank() || assurance.isBlank()) {
            throw new IllegalArgumentException("claimContractVersion and assurance must be non-blank");
        }
        if (securityEpoch != null && securityEpoch < 0) {
            throw new IllegalArgumentException("securityEpoch must be non-negative");
        }
        requireProfileConsistency(tokenProfile, principalSubjectRef);
        audiences = Set.copyOf(audiences);
        scopes = Set.copyOf(scopes);
    }

    public boolean isHuman() {
        return principalSubjectRef instanceof HumanSubjectRef;
    }

    public boolean isService() {
        return principalSubjectRef instanceof ServiceSubjectRef;
    }

    public String subjectId() {
        return principalSubjectRef.subjectId();
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    private static void requireProfileConsistency(
            TokenProfile profile, PrincipalSubjectRef principal) {
        boolean userProfile = profile == TokenProfile.USER_NEUTRAL_V1
                || profile == TokenProfile.USER_WORKSPACE_V1;
        boolean human = principal instanceof HumanSubjectRef;
        if (userProfile != human) {
            throw new IllegalArgumentException(
                    "TokenProfile " + profile + " is inconsistent with principal kind "
                            + principal.getClass().getSimpleName());
        }
    }
}
