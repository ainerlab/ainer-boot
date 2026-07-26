package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.application.PlatformIdentityQueryRepository;
import dev.ainer.module.identity.account.application.PlatformIdentityQueryService;
import dev.ainer.module.identity.account.application.PlatformIdentityTenantProjection;
import dev.ainer.module.identity.account.application.PlatformIdentityUserProjection;
import dev.ainer.module.identity.account.application.PlatformProvisioningActor;
import dev.ainer.module.identity.account.application.TenantProvisioningCancellationResult;
import dev.ainer.module.identity.account.application.TenantProvisioningPolicy;
import dev.ainer.module.identity.account.application.TenantProvisioningRequest;
import dev.ainer.module.identity.account.application.TenantProvisioningService;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import dev.ainer.security.service.AuthenticatedService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformIdentityControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-26T08:00:00Z");
    private static final String OPERATOR = "platform-operator";
    private static final PlatformIdentityControlSettings SETTINGS =
            new PlatformIdentityControlSettings(
                    Set.of(OPERATOR),
                    new TenantProvisioningPolicy(
                            Duration.ofDays(7),
                            Duration.ofHours(24),
                            5),
                    "test-v1",
                    Map.of("test-v1", new byte[32]));

    private final PlatformIdentityActorResolver actorResolver =
            new PlatformIdentityActorResolver(SETTINGS);

    @Test
    void tenantAndUserPagesRequireTheirOwnReadScope() {
        PlatformIdentityQueryController controller =
                new PlatformIdentityQueryController(
                        new PlatformIdentityQueryService(new StubQueryRepository()),
                        actorResolver);

        var tenants = controller.tenants(
                1,
                20,
                authentication(OPERATOR, null, "SCOPE_platform.tenants.read"),
                new MockHttpServletRequest());
        var users = controller.users(
                1,
                20,
                authentication(OPERATOR, null, "SCOPE_platform.users.read"),
                new MockHttpServletRequest());

        assertThat(tenants.data().items()).singleElement()
                .extracting(PlatformIdentityQueryController.TenantResponse::code)
                .isEqualTo("acme");
        assertThat(users.data().items()).singleElement()
                .extracting(PlatformIdentityQueryController.UserResponse::username)
                .isEqualTo("owner@acme.dev");
        assertForbidden(() -> controller.users(
                1,
                20,
                authentication(OPERATOR, null, "SCOPE_platform.tenants.read"),
                new MockHttpServletRequest()));
    }

    @Test
    void rejectsTenantBoundOrUnknownPlatformService() {
        PlatformIdentityQueryController controller =
                new PlatformIdentityQueryController(
                        new PlatformIdentityQueryService(new StubQueryRepository()),
                        actorResolver);

        assertForbidden(() -> controller.tenants(
                1,
                20,
                authentication(
                        OPERATOR,
                        UUID.randomUUID().toString(),
                        "SCOPE_platform.tenants.read"),
                new MockHttpServletRequest()));
        assertForbidden(() -> controller.tenants(
                1,
                20,
                authentication("unknown-operator", null, "SCOPE_platform.tenants.read"),
                new MockHttpServletRequest()));
    }

    @Test
    void explicitCancellationRequiresBothWriteScopesAndCountsOnlyTransition() {
        StubProvisioningService service = new StubProvisioningService();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PlatformTenantProvisioningController controller =
                new PlatformTenantProvisioningController(
                        service,
                        SETTINGS,
                        actorResolver,
                        meterRegistry);

        var response = controller.cancel(
                service.request.id(),
                new PlatformTenantProvisioningController.CancelTenantProvisioningRequest(
                        "ORDER-CANCEL-1"),
                authentication(
                        OPERATOR,
                        null,
                        "SCOPE_platform.tenants.write",
                        "SCOPE_platform.users.write"),
                new MockHttpServletRequest());
        controller.cancel(
                service.request.id(),
                new PlatformTenantProvisioningController.CancelTenantProvisioningRequest(
                        "ORDER-CANCEL-REPLAY"),
                authentication(
                        OPERATOR,
                        null,
                        "SCOPE_platform.tenants.write",
                        "SCOPE_platform.users.write"),
                new MockHttpServletRequest());

        assertThat(response.data().status()).isEqualTo("CANCELLED");
        assertThat(service.changeReferences)
                .containsExactly("ORDER-CANCEL-1", "ORDER-CANCEL-REPLAY");
        assertThat(meterRegistry
                .counter("ainer.identity.tenant.provisioning.cancelled")
                .count()).isEqualTo(1);
        assertForbidden(() -> controller.cancel(
                service.request.id(),
                new PlatformTenantProvisioningController.CancelTenantProvisioningRequest(
                        "ORDER-CANCEL-2"),
                authentication(OPERATOR, null, "SCOPE_platform.tenants.write"),
                new MockHttpServletRequest()));
    }

    private Authentication authentication(
            String subject,
            String tenantId,
            String... authorities) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim(
                        AuthenticatedService.ACTOR_TYPE_CLAIM,
                        AuthenticatedService.SERVICE_ACTOR_TYPE);
        if (tenantId != null) {
            jwt.claim("tenant_id", tenantId);
        }
        return new JwtAuthenticationToken(
                jwt.build(),
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    private void assertForbidden(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(
                                StandardErrorCode.FORBIDDEN));
    }

    private static final class StubQueryRepository
            implements PlatformIdentityQueryRepository {

        @Override
        public List<PlatformIdentityTenantProjection> findTenants(long offset, int limit) {
            return List.of(new PlatformIdentityTenantProjection(
                    UUID.randomUUID(),
                    "acme",
                    "Acme",
                    IdentityStatus.ACTIVE,
                    NOW,
                    NOW));
        }

        @Override
        public long countTenants() {
            return 1;
        }

        @Override
        public List<PlatformIdentityUserProjection> findUsers(long offset, int limit) {
            return List.of(new PlatformIdentityUserProjection(
                    UUID.randomUUID(),
                    "owner@acme.dev",
                    "Owner",
                    IdentityStatus.ACTIVE,
                    NOW,
                    NOW));
        }

        @Override
        public long countUsers() {
            return 1;
        }
    }

    private static final class StubProvisioningService extends TenantProvisioningService {

        private final TenantProvisioningRequest request = new TenantProvisioningRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acme",
                "Acme",
                UUID.randomUUID(),
                "owner@acme.dev",
                "Owner",
                false,
                "REQUESTED",
                "idem-acme",
                "a".repeat(64),
                OPERATOR,
                "req-create",
                "ORDER-CREATE-1",
                NOW,
                NOW.plus(Duration.ofDays(1)),
                null,
                0);
        private final ArrayList<String> changeReferences = new ArrayList<>();
        private boolean firstCancellation = true;

        private StubProvisioningService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public TenantProvisioningCancellationResult cancel(
                UUID provisioningRequestId,
                String changeReference,
                PlatformProvisioningActor actor) {
            changeReferences.add(changeReference);
            boolean cancelled = firstCancellation;
            firstCancellation = false;
            return new TenantProvisioningCancellationResult(
                    request.cancelled(NOW.plusSeconds(1)),
                    cancelled);
        }
    }
}
