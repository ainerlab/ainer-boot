package dev.ainer.module.ai.agent;

import dev.ainer.core.error.BusinessException;
import dev.ainer.module.ai.agent.application.AiAgentApplicationService;
import dev.ainer.module.ai.agent.application.AiAgentAuthorities;
import dev.ainer.module.ai.agent.application.AiAgentErrorCode;
import dev.ainer.module.ai.agent.application.AiAgentRepository;
import dev.ainer.module.ai.agent.domain.AiAgentDefinition;
import dev.ainer.security.principal.HumanSubjectRef;
import dev.ainer.security.principal.IdentityAuthorityRef;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.TokenProfile;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentApplicationServiceTest {

    private static final IdentityAuthorityRef AUTHORITY =
            new IdentityAuthorityRef("https://auth.ainer.test");

    @Test
    void pageRejectsInvalidSize() {
        AiAgentApplicationService service = new AiAgentApplicationService(
                new EmptyAgentRepository(), Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC));
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                new HumanSubjectRef(AUTHORITY, "account:1"),
                AUTHORITY,
                TokenProfile.USER_NEUTRAL_V1,
                "1",
                Set.of("ainer-api"),
                Set.of(AiAgentAuthorities.MANAGE),
                "pwd",
                null,
                0L);
        assertThatThrownBy(() -> service.page(principal, 1, 101))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(AiAgentErrorCode.INVALID_PAGE));
        assertThatThrownBy(() -> service.page(principal, 0, 20))
                .isInstanceOf(BusinessException.class);
    }

    private static final class EmptyAgentRepository implements AiAgentRepository {

        @Override
        public void insert(AiAgentDefinition agent) {
        }

        @Override
        public Optional<AiAgentDefinition> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public void retire(UUID id, Instant at) {
        }

        @Override
        public List<AiAgentDefinition> page(long offset, int limit) {
            return List.of();
        }

        @Override
        public long count() {
            return 0;
        }
    }
}
