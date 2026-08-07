package dev.ainer.module.identity.foundation;

import dev.ainer.core.error.BusinessException;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.principal.ServiceSubjectRef;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the Greenfield ServicePrincipal core end-to-end without a database (S1.1 spine). Covers
 * principal registration, OAuth client binding, client-id → principal resolution, the stable-principal
 * invariant and the hard-conflict rule mandated by ADR-0033 Greenfield §2.6.
 */
class ServicePrincipalFoundationServiceTest {

    private static final IdentityAuthorityRef AINER =
            new IdentityAuthorityRef("https://ainer.example/auth");

    private final Map<UUID, OAuthClientBinding> bindingStore = new HashMap<>();
    private final InMemoryServicePrincipalRepository principals = new InMemoryServicePrincipalRepository(bindingStore);
    private final InMemoryOAuthClientBindingRepository bindings = new InMemoryOAuthClientBindingRepository(bindingStore);
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-08-06T10:00:00Z"), ZoneOffset.UTC);
    private final Supplier<UUID> ids = sequentialIds();

    private final ServicePrincipalFoundationService service =
            new ServicePrincipalFoundationService(principals, bindings, clock, ids);

    @Test
    void registerCreatesActivePrincipalWithZeroEpoch() {
        ServicePrincipal principal = service.registerServicePrincipal(AINER);

        assertThat(principal.status()).isEqualTo(ServicePrincipalStatus.ACTIVE);
        assertThat(principal.securityEpoch()).isZero();
        assertThat(principal.authority()).isEqualTo(AINER);
        ServiceSubjectRef subjectRef = principal.toSubjectRef();
        assertThat(subjectRef.authority()).isEqualTo(AINER);
        assertThat(subjectRef.servicePrincipalId()).isEqualTo(principal.principalId().toString());
    }

    @Test
    void bindLinksClientToActivePrincipal() {
        ServicePrincipal principal = service.registerServicePrincipal(AINER);

        OAuthClientBinding binding = service.bindClient(principal.principalId(), "ainer-machine-client");

        assertThat(binding.principalId()).isEqualTo(principal.principalId());
        assertThat(binding.clientId()).isEqualTo("ainer-machine-client");
        assertThat(binding.isActive()).isTrue();
        assertThat(binding.unboundAt()).isNull();

        Optional<ServicePrincipal> resolved = service.findPrincipalByClientId("ainer-machine-client");
        assertThat(resolved).contains(principal);
    }

    @Test
    void bindRejectsUnknownPrincipal() {
        assertThatThrownBy(() -> service.bindClient(UUID.randomUUID(), "orphan-client"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.SERVICE_PRINCIPAL_NOT_FOUND);
    }

    @Test
    void bindRejectsDuplicateActiveClientId() {
        ServicePrincipal first = service.registerServicePrincipal(AINER);
        ServicePrincipal second = service.registerServicePrincipal(AINER);
        service.bindClient(first.principalId(), "shared-client");

        assertThatThrownBy(() -> service.bindClient(second.principalId(), "shared-client"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.OAUTH_CLIENT_BINDING_ALREADY_EXISTS);
    }

    @Test
    void resolveReturnsEmptyForUnboundClientId() {
        assertThat(service.findPrincipalByClientId("nobody-bound-this")).isEmpty();
    }

    @Test
    void subjectRefStaysStableAcrossClientIdRotation() {
        ServicePrincipal principal = service.registerServicePrincipal(AINER);
        service.bindClient(principal.principalId(), "client-v1");

        ServicePrincipal resolvedV1 = service.findPrincipalByClientId("client-v1").orElseThrow();
        String stableSubject = resolvedV1.toSubjectRef().servicePrincipalId();

        // credential rotation: retire v1, bind v2 to the same principal
        bindings.retireByClientId("client-v1");
        service.bindClient(principal.principalId(), "client-v2");

        assertThat(service.findPrincipalByClientId("client-v1")).isEmpty();
        ServicePrincipal resolvedV2 = service.findPrincipalByClientId("client-v2").orElseThrow();
        assertThat(resolvedV2.toSubjectRef().servicePrincipalId()).isEqualTo(stableSubject);
    }

    private static Supplier<UUID> sequentialIds() {
        AtomicLong counter = new AtomicLong(1);
        return () -> new UUID(0L, counter.getAndIncrement());
    }

    private static final class InMemoryServicePrincipalRepository implements ServicePrincipalRepository {
        private final Map<UUID, ServicePrincipal> store = new HashMap<>();
        private final Map<UUID, OAuthClientBinding> bindingStore;

        InMemoryServicePrincipalRepository(Map<UUID, OAuthClientBinding> bindingStore) {
            this.bindingStore = bindingStore;
        }

        @Override
        public void save(ServicePrincipal principal) {
            store.put(principal.principalId(), principal);
        }

        @Override
        public Optional<ServicePrincipal> findByPrincipalId(UUID principalId) {
            return Optional.ofNullable(store.get(principalId));
        }

        @Override
        public Optional<ServicePrincipal> findByActiveClientId(String clientId) {
            return bindingStore.values().stream()
                    .filter(b -> b.clientId().equals(clientId) && b.isActive())
                    .map(OAuthClientBinding::principalId)
                    .map(store::get)
                    .filter(Objects::nonNull)
                    .findFirst();
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }
    }

    private static final class InMemoryOAuthClientBindingRepository implements OAuthClientBindingRepository {
        private final Map<UUID, OAuthClientBinding> store;

        InMemoryOAuthClientBindingRepository(Map<UUID, OAuthClientBinding> store) {
            this.store = store;
        }

        @Override
        public void save(OAuthClientBinding binding) {
            store.put(binding.bindingId(), binding);
        }

        @Override
        public Optional<OAuthClientBinding> findActiveByClientId(String clientId) {
            return store.values().stream()
                    .filter(b -> b.clientId().equals(clientId) && b.isActive())
                    .findFirst();
        }

        @Override
        public Optional<OAuthClientBinding> findByPrincipalId(UUID principalId) {
            return store.values().stream()
                    .filter(b -> b.principalId().equals(principalId))
                    .reduce((first, second) -> second);
        }

        @Override
        public UUID nextUuidV7() {
            return UUID.randomUUID();
        }

        void retireByClientId(String clientId) {
            store.values().stream()
                    .filter(b -> b.clientId().equals(clientId) && b.isActive())
                    .findFirst()
                    .ifPresent(active -> {
                        OAuthClientBinding retired = new OAuthClientBinding(
                                active.bindingId(), active.principalId(), active.clientId(),
                                OAuthClientBindingStatus.RETIRED, active.boundAt(),
                                Instant.parse("2026-08-06T11:00:00Z"));
                        store.put(active.bindingId(), retired);
                    });
        }
    }
}
