package dev.ainer.authorizationserver.config;

import dev.ainer.authorizationserver.identity.AinerUserDetails;
import dev.ainer.authorizationserver.identity.AinerUserDetailsService;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.account.application.IdentityAccount;
import dev.ainer.module.identity.account.application.IdentityDirectoryEntry;
import dev.ainer.module.identity.account.application.IdentityRepository;
import dev.ainer.module.identity.account.domain.IdentityAccessEvent;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.module.identity.account.domain.IdentityTenant;
import dev.ainer.module.identity.account.domain.IdentityUser;
import dev.ainer.module.identity.account.domain.TenantMembership;
import dev.ainer.module.identity.account.domain.TenantRole;
import dev.ainer.module.identity.account.application.OwnershipRecovery;
import dev.ainer.module.identity.account.application.OwnershipTransfer;
import dev.ainer.module.identity.account.application.TenantContextEntry;
import dev.ainer.module.identity.foundation.AccountStatus;
import dev.ainer.module.identity.foundation.HumanAccount;
import dev.ainer.module.identity.foundation.HumanAccountRepository;
import dev.ainer.module.identity.foundation.OAuthClientBinding;
import dev.ainer.module.identity.foundation.OAuthClientBindingRepository;
import dev.ainer.module.identity.foundation.OAuthClientBindingStatus;
import dev.ainer.module.identity.foundation.ServicePrincipal;
import dev.ainer.module.identity.foundation.ServicePrincipalFoundationService;
import dev.ainer.module.identity.foundation.ServicePrincipalRepository;
import dev.ainer.module.identity.foundation.ServicePrincipalStatus;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.TokenProfile;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AinerJwtTokenCustomizerTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://ainer.example/auth");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Map<String, OAuthClientBinding> activeBindings = new HashMap<>();
    private final InMemoryPrincipalRepository principals = new InMemoryPrincipalRepository(activeBindings);
    private final InMemoryBindingRepository bindings = new InMemoryBindingRepository(activeBindings);
    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryIdentityRepository identity = new InMemoryIdentityRepository();

    private final ServicePrincipalFoundationService principalService =
            new ServicePrincipalFoundationService(principals, bindings, clock, principals::nextUuidV7);

    private final AinerJwtTokenCustomizer customizer = new AinerJwtTokenCustomizer(
            new AinerAuthorizationServerProperties(
                    null, null, null, null, null, null, null, null, null, null, null, null),
            new AinerUserDetailsService(new IdentityApplicationService(identity, raw -> raw, clock)),
            new IdentityApplicationService(identity, raw -> raw, clock),
            principalService,
            accounts);

    @Test
    void serviceV1ProjectsStablePrincipalClaims() {
        ServicePrincipal principal = principalService.registerServicePrincipal(AUTHORITY);
        String clientId = "svc-client-1";
        principalService.bindClient(principal.principalId(), clientId);
        RegisteredClient client = client(clientId, TokenProfile.SERVICE_V1.claimValue(), null);
        JwtEncodingContext context = context(
                client, machinePrincipal(), AuthorizationGrantType.CLIENT_CREDENTIALS);

        customizer.customize(context);

        Map<String, Object> claims = claimsOf(context);
        assertThat(claims).containsEntry("sub", principal.principalId().toString());
        assertThat(claims).containsEntry(TokenProfile.PROFILE_CLAIM, TokenProfile.SERVICE_V1.claimValue());
        assertThat(claims).containsEntry(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION);
        assertThat(claims).containsEntry("actor_type", "SERVICE");
        assertThat(claims).containsEntry(AinerAuthorizationServerConfiguration.SEC_EPOCH_CLAIM, 0L);
        assertThat(claims).doesNotContainKey("tenant_id");
        assertThat(claims).containsEntry("aud", List.of("ainer-api"));
    }

    @Test
    void serviceV1IgnoresTenantSettingAndNeverEmitsTenantClaim() {
        ServicePrincipal principal = principalService.registerServicePrincipal(AUTHORITY);
        String clientId = "svc-client-2";
        principalService.bindClient(principal.principalId(), clientId);
        RegisteredClient client = client(clientId, TokenProfile.SERVICE_V1.claimValue(), "tenant:legacy");
        JwtEncodingContext context = context(
                client, machinePrincipal(), AuthorizationGrantType.CLIENT_CREDENTIALS);

        customizer.customize(context);

        Map<String, Object> claims = claimsOf(context);
        assertThat(claims).containsEntry("sub", principal.principalId().toString());
        assertThat(claims).doesNotContainKey("tenant_id");
    }

    @Test
    void serviceV1WithoutBindingFailsClosed() {
        RegisteredClient client = client("svc-client-unbound", TokenProfile.SERVICE_V1.claimValue(), null);

        assertThatThrownBy(() -> customizer.customize(
                context(client, machinePrincipal(), AuthorizationGrantType.CLIENT_CREDENTIALS)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("No ACTIVE ServicePrincipal bound to client svc-client-unbound");
    }

    @Test
    void serviceV1WithDisabledPrincipalFailsClosed() {
        ServicePrincipal principal = principalService.registerServicePrincipal(AUTHORITY);
        String clientId = "svc-client-3";
        principalService.bindClient(principal.principalId(), clientId);
        principals.setStatus(principal.principalId(), ServicePrincipalStatus.DISABLED);
        RegisteredClient client = client(clientId, TokenProfile.SERVICE_V1.claimValue(), null);

        assertThatThrownBy(() -> customizer.customize(
                context(client, machinePrincipal(), AuthorizationGrantType.CLIENT_CREDENTIALS)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("is not active");
    }

    @Test
    void userNeutralV1ProjectsHumanAccountClaims() {
        UUID accountId = UUID.randomUUID();
        accounts.save(new HumanAccount(
                accountId, AUTHORITY, AccountStatus.ACTIVE, 7L, NOW));
        RegisteredClient client = client("user-client-1", TokenProfile.USER_NEUTRAL_V1.claimValue(), null);
        AinerUserDetails user = new AinerUserDetails(
                UUID.randomUUID(), UUID.randomUUID(), accountId, 7L,
                "alice", "password", true, true, List.of());
        FactorGrantedAuthority pwd = FactorGrantedAuthority
                .withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(NOW).build();
        JwtEncodingContext context = context(
                client,
                UsernamePasswordAuthenticationToken.authenticated(user, "password", List.of(pwd)),
                AuthorizationGrantType.AUTHORIZATION_CODE);

        customizer.customize(context);

        Map<String, Object> claims = claimsOf(context);
        assertThat(claims).containsEntry("sub", accountId.toString());
        assertThat(claims).containsEntry(TokenProfile.PROFILE_CLAIM, TokenProfile.USER_NEUTRAL_V1.claimValue());
        assertThat(claims).containsEntry(TokenProfile.CONTRACT_VERSION_CLAIM, TokenProfile.CURRENT_CONTRACT_VERSION);
        assertThat(claims).containsEntry("actor_type", "USER");
        assertThat(claims).containsEntry(AinerAuthorizationServerConfiguration.SEC_EPOCH_CLAIM, 7L);
        assertThat(claims).containsEntry(IdTokenClaimNames.AMR, List.of("pwd"));
        assertThat(claims.get(IdTokenClaimNames.AUTH_TIME)).isNotNull();
        assertThat(claims).doesNotContainKey("tenant_id");
        assertThat(claims).doesNotContainKey("roles");
    }

    @Test
    void userNeutralV1WithoutFoundationAccountFailsClosed() {
        RegisteredClient client = client("user-client-2", TokenProfile.USER_NEUTRAL_V1.claimValue(), null);
        AinerUserDetails user = new AinerUserDetails(
                UUID.randomUUID(), UUID.randomUUID(),
                "alice", "password", true, true, List.of());

        assertThatThrownBy(() -> customizer.customize(context(
                client,
                UsernamePasswordAuthenticationToken.authenticated(user, "password", List.of()),
                AuthorizationGrantType.AUTHORIZATION_CODE)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("requires a foundation account");
    }

    @Test
    void userNeutralV1WithMissingAccountFailsClosed() {
        RegisteredClient client = client("user-client-3", TokenProfile.USER_NEUTRAL_V1.claimValue(), null);
        AinerUserDetails user = new AinerUserDetails(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0L,
                "alice", "password", true, true, List.of());

        assertThatThrownBy(() -> customizer.customize(context(
                client,
                UsernamePasswordAuthenticationToken.authenticated(user, "password", List.of()),
                AuthorizationGrantType.AUTHORIZATION_CODE)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("HumanAccount not found");
    }

    @Test
    void userNeutralV1WithInactiveAccountFailsClosed() {
        UUID accountId = UUID.randomUUID();
        accounts.save(new HumanAccount(
                accountId, AUTHORITY, AccountStatus.DISABLED, 7L, NOW));
        RegisteredClient client = client("user-client-4", TokenProfile.USER_NEUTRAL_V1.claimValue(), null);
        AinerUserDetails user = new AinerUserDetails(
                UUID.randomUUID(), UUID.randomUUID(), accountId, 7L,
                "alice", "password", true, true, List.of());

        assertThatThrownBy(() -> customizer.customize(context(
                client,
                UsernamePasswordAuthenticationToken.authenticated(user, "password", List.of()),
                AuthorizationGrantType.AUTHORIZATION_CODE)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("is not active");
    }

    @Test
    void unknownTokenProfileFailsClosed() {
        RegisteredClient client = client("legacy-tenant", "LEGACY_TENANT", null);

        assertThatThrownBy(() -> customizer.customize(
                context(client, machinePrincipal(), AuthorizationGrantType.CLIENT_CREDENTIALS)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Unknown token profile: LEGACY_TENANT");
    }

    @Test
    void legacyUserTokenKeepsTenantClaims() {
        UUID tenantId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        identity.addDirectoryEntry(new IdentityDirectoryEntry(
                tenantId, subjectId, "alice", "Alice", TenantRole.OWNER));
        RegisteredClient client = client("legacy-user-client", null, null);
        AinerUserDetails user = new AinerUserDetails(
                subjectId, tenantId, "alice", "password", true, true, List.of());
        JwtEncodingContext context = context(
                client,
                UsernamePasswordAuthenticationToken.authenticated(user, "password", List.of()),
                AuthorizationGrantType.AUTHORIZATION_CODE);

        customizer.customize(context);

        Map<String, Object> claims = claimsOf(context);
        assertThat(claims).containsEntry("sub", subjectId.toString());
        assertThat(claims).containsEntry("actor_type", "USER");
        assertThat(claims).containsEntry("tenant_id", tenantId.toString());
        assertThat(claims).containsEntry("roles", List.of(TenantRole.OWNER.name()));
        assertThat(claims).doesNotContainKey(TokenProfile.PROFILE_CLAIM);
    }

    @Test
    void legacyMachineTokenKeepsTenantClaims() {
        RegisteredClient client = client("legacy-machine-client", null, "tenant:legacy");
        JwtEncodingContext context = context(
                client, machinePrincipal(), AuthorizationGrantType.CLIENT_CREDENTIALS);

        customizer.customize(context);

        Map<String, Object> claims = claimsOf(context);
        assertThat(claims).containsEntry("sub", "legacy-machine-client");
        assertThat(claims).containsEntry("actor_type", "SERVICE");
        assertThat(claims).containsEntry("tenant_id", "tenant:legacy");
        assertThat(claims).doesNotContainKey(TokenProfile.PROFILE_CLAIM);
    }

    private static Map<String, Object> claimsOf(JwtEncodingContext context) {
        return context.getClaims().build().getClaims();
    }

    private static Authentication machinePrincipal() {
        return UsernamePasswordAuthenticationToken.unauthenticated("client", "password");
    }

    private static RegisteredClient client(String clientId, String tokenProfile, String tenantId) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret("{noop}secret")
                .clientName("test client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.ainer.test/callback")
                .scope("ai.invoke");
        ClientSettings.Builder settings = ClientSettings.builder();
        if (tokenProfile != null) {
            settings.setting(AinerAuthorizationServerConfiguration.TOKEN_PROFILE_SETTING, tokenProfile);
        }
        if (tenantId != null) {
            settings.setting(AinerAuthorizationServerConfiguration.CLIENT_TENANT_SETTING, tenantId);
        }
        return builder.clientSettings(settings.build()).build();
    }

    private static JwtEncodingContext context(
            RegisteredClient client,
            Authentication principal,
            AuthorizationGrantType grantType) {
        return JwtEncodingContext
                .with(JwsHeader.with(MacAlgorithm.HS256), JwtClaimsSet.builder())
                .registeredClient(client)
                .principal(principal)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(grantType)
                .build();
    }

    private static final class InMemoryPrincipalRepository implements ServicePrincipalRepository {

        private final Map<UUID, ServicePrincipal> byId = new HashMap<>();
        private final Map<String, OAuthClientBinding> activeBindings;

        private InMemoryPrincipalRepository(Map<String, OAuthClientBinding> activeBindings) {
            this.activeBindings = activeBindings;
        }

        @Override
        public void save(ServicePrincipal principal) {
            byId.put(principal.principalId(), principal);
        }

        @Override
        public Optional<ServicePrincipal> findByPrincipalId(UUID principalId) {
            return Optional.ofNullable(byId.get(principalId));
        }

        @Override
        public Optional<ServicePrincipal> findByActiveClientId(String clientId) {
            OAuthClientBinding binding = activeBindings.get(clientId);
            if (binding == null || binding.status() != OAuthClientBindingStatus.ACTIVE) {
                return Optional.empty();
            }
            return Optional.ofNullable(byId.get(binding.principalId()));
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }

        private void setStatus(UUID principalId, ServicePrincipalStatus status) {
            byId.computeIfPresent(principalId, (id, principal) -> new ServicePrincipal(
                    principal.principalId(), principal.authority(), status,
                    principal.securityEpoch(), principal.createdAt()));
        }
    }

    private static final class InMemoryBindingRepository implements OAuthClientBindingRepository {

        private final Map<String, OAuthClientBinding> activeBindings;

        private InMemoryBindingRepository(Map<String, OAuthClientBinding> activeBindings) {
            this.activeBindings = activeBindings;
        }

        @Override
        public void save(OAuthClientBinding binding) {
            if (binding.status() == OAuthClientBindingStatus.ACTIVE) {
                activeBindings.put(binding.clientId(), binding);
            }
        }

        @Override
        public Optional<OAuthClientBinding> findActiveByClientId(String clientId) {
            return Optional.ofNullable(activeBindings.get(clientId));
        }

        @Override
        public Optional<OAuthClientBinding> findByPrincipalId(UUID principalId) {
            return activeBindings.values().stream()
                    .filter(binding -> binding.principalId().equals(principalId))
                    .findFirst();
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }
    }

    private static final class InMemoryAccountRepository implements HumanAccountRepository {

        private final Map<UUID, HumanAccount> byId = new HashMap<>();

        @Override
        public void save(HumanAccount account) {
            byId.put(account.accountId(), account);
        }

        @Override
        public Optional<HumanAccount> findByAccountId(UUID accountId) {
            return Optional.ofNullable(byId.get(accountId));
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }
    }

    private static final class InMemoryIdentityRepository implements IdentityRepository {

        private final Map<UUID, IdentityDirectoryEntry> directoryBySubject = new HashMap<>();

        private void addDirectoryEntry(IdentityDirectoryEntry entry) {
            directoryBySubject.put(entry.subjectId(), entry);
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDirectoryEntry(UUID tenantId, UUID subjectId) {
            return Optional.ofNullable(directoryBySubject.get(subjectId))
                    .filter(entry -> entry.tenantId().equals(tenantId));
        }

        @Override
        public void insertTenant(IdentityTenant tenant) {
            unsupported();
        }

        @Override
        public void insertUser(IdentityUser user) {
            unsupported();
        }

        @Override
        public void insertMembership(TenantMembership membership) {
            unsupported();
        }

        @Override
        public Optional<IdentityAccount> findAccountByUsername(String normalizedUsername) {
            return unsupported();
        }

        @Override
        public Optional<IdentityAccount> findAccountBySubjectId(UUID subjectId) {
            return unsupported();
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDirectoryEntryForUpdate(UUID tenantId, UUID subjectId) {
            return unsupported();
        }

        @Override
        public List<IdentityDirectoryEntry> searchActiveDirectory(UUID tenantId, String likePattern, int limit) {
            return unsupported();
        }

        @Override
        public Optional<IdentityStatus> findUserStatusForUpdate(UUID subjectId) {
            return unsupported();
        }

        @Override
        public List<UUID> findActiveMembershipTenantIds(UUID subjectId) {
            return unsupported();
        }

        @Override
        public List<TenantContextEntry> findActiveMembershipsBySubject(UUID subjectId) {
            return unsupported();
        }

        @Override
        public boolean updateUserStatus(
                UUID subjectId, IdentityStatus expectedStatus, IdentityStatus newStatus, Instant updatedAt) {
            return unsupported();
        }

        @Override
        public Optional<TenantMembership> findMembershipForUpdate(UUID tenantId, UUID subjectId) {
            return unsupported();
        }

        @Override
        public boolean updateMembershipStatus(
                UUID tenantId,
                UUID subjectId,
                IdentityStatus expectedStatus,
                IdentityStatus newStatus,
                Instant updatedAt) {
            return unsupported();
        }

        @Override
        public List<IdentityDirectoryEntry> listMembersByTenant(UUID tenantId, int offset, int limit) {
            return unsupported();
        }

        @Override
        public int countMembersByTenant(UUID tenantId) {
            return unsupported();
        }

        @Override
        public boolean updateMembershipRole(UUID tenantId, UUID subjectId, String newRole, Instant updatedAt) {
            return unsupported();
        }

        @Override
        public boolean reactivateMembership(
                UUID tenantId,
                UUID subjectId,
                IdentityStatus expectedStatus,
                String newRole,
                Instant updatedAt) {
            return unsupported();
        }

        @Override
        public void insertMemberAudit(
                UUID tenantId, UUID actorSubjectId, UUID targetSubjectId,
                String operation, String role, String reasonCode, String requestId, Instant occurredAt) {
            unsupported();
        }

        @Override
        public Optional<IdentityDirectoryEntry> findActiveDefaultOwner(String tenantCode, String normalizedUsername) {
            return unsupported();
        }

        @Override
        public boolean tenantExistsByCode(String tenantCode) {
            return unsupported();
        }

        @Override
        public boolean userExistsByUsername(String normalizedUsername) {
            return unsupported();
        }

        @Override
        public void acquireIdentityLock(String lockKey) {
            unsupported();
        }

        @Override
        public boolean openProvisioningReservationExists(String tenantCode, String normalizedUsername) {
            return unsupported();
        }

        @Override
        public void insertAccessEvent(IdentityAccessEvent event) {
            unsupported();
        }

        @Override
        public void insertOwnershipTransfer(OwnershipTransfer transfer) {
            unsupported();
        }

        @Override
        public Optional<OwnershipTransfer> findOwnershipTransfer(UUID id) {
            return unsupported();
        }

        @Override
        public Optional<OwnershipTransfer> findOwnershipTransferForUpdate(UUID id) {
            return unsupported();
        }

        @Override
        public boolean completeOwnershipTransfer(
                UUID id, UUID tenantId, UUID executedBySubjectId,
                Instant executedAt, Instant updatedAt) {
            return unsupported();
        }

        @Override
        public boolean cancelOwnershipTransfer(UUID id, UUID tenantId, Instant updatedAt) {
            return unsupported();
        }

        @Override
        public void insertOwnershipRecovery(OwnershipRecovery recovery) {
            unsupported();
        }

        @Override
        public Optional<OwnershipRecovery> findOwnershipRecovery(UUID id) {
            return unsupported();
        }

        @Override
        public Optional<OwnershipRecovery> findOwnershipRecoveryForUpdate(UUID id) {
            return unsupported();
        }

        @Override
        public boolean executeOwnershipRecovery(
                UUID id, UUID tenantId, String approverServiceId,
                Instant executedAt, Instant updatedAt) {
            return unsupported();
        }

        @Override
        public boolean cancelOwnershipRecovery(UUID id, UUID tenantId, Instant updatedAt) {
            return unsupported();
        }

        @Override
        public void insertSecurityOperationAudit(
                UUID operationId, UUID tenantId, UUID targetId, String operationType,
                String phase, String actorServiceId, String incidentReference, Instant occurredAt) {
            unsupported();
        }

        private static <T> T unsupported() {
            throw new UnsupportedOperationException("not used in AinerJwtTokenCustomizerTest");
        }
    }
}
