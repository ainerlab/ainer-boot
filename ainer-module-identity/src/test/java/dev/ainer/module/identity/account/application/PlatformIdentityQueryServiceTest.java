package dev.ainer.module.identity.account.application;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.module.identity.account.domain.IdentityStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformIdentityQueryServiceTest {

    private static final PlatformProvisioningActor ACTOR =
            new PlatformProvisioningActor("platform-operator", null, "req-directory");

    private final StubRepository repository = new StubRepository();
    private final PlatformIdentityQueryService service =
            new PlatformIdentityQueryService(repository);

    @Test
    void returnsBoundedTenantAndUserPages() {
        PlatformIdentityTenantPage tenants = service.tenants(ACTOR, 2, 20);
        PlatformIdentityUserPage users = service.users(ACTOR, 3, 10);

        assertThat(tenants.items()).hasSize(1);
        assertThat(tenants.page()).isEqualTo(2);
        assertThat(tenants.size()).isEqualTo(20);
        assertThat(tenants.total()).isEqualTo(41);
        assertThat(repository.tenantOffset).isEqualTo(20);
        assertThat(users.items()).hasSize(1);
        assertThat(users.page()).isEqualTo(3);
        assertThat(users.size()).isEqualTo(10);
        assertThat(users.total()).isEqualTo(22);
        assertThat(repository.userOffset).isEqualTo(20);
    }

    @Test
    void rejectsInvalidPageAndTenantBoundActor() {
        assertIdentityError(
                () -> service.tenants(ACTOR, 1, 101),
                IdentityErrorCode.INVALID_DIRECTORY_QUERY);
        assertStandardError(
                () -> service.users(
                        new PlatformProvisioningActor(
                                "platform-operator",
                                UUID.randomUUID().toString(),
                                "req-directory"),
                        1,
                        20),
                StandardErrorCode.FORBIDDEN);
    }

    private void assertIdentityError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            IdentityErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private void assertStandardError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            StandardErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private static final class StubRepository implements PlatformIdentityQueryRepository {

        private long tenantOffset;
        private long userOffset;

        @Override
        public List<PlatformIdentityTenantProjection> findTenants(long offset, int limit) {
            tenantOffset = offset;
            return List.of(new PlatformIdentityTenantProjection(
                    UUID.randomUUID(),
                    "acme",
                    "Acme",
                    IdentityStatus.ACTIVE,
                    Instant.EPOCH,
                    Instant.EPOCH));
        }

        @Override
        public long countTenants() {
            return 41;
        }

        @Override
        public List<PlatformIdentityUserProjection> findUsers(long offset, int limit) {
            userOffset = offset;
            return List.of(new PlatformIdentityUserProjection(
                    UUID.randomUUID(),
                    "owner@acme.dev",
                    "Owner",
                    IdentityStatus.ACTIVE,
                    Instant.EPOCH,
                    Instant.EPOCH));
        }

        @Override
        public long countUsers() {
            return 22;
        }
    }
}
