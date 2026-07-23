package dev.ainer.security.actor;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedActorTest {

    @Test
    void normalizesTrustedIdentifiersAndCopiesAuthorities() {
        AuthenticatedActor actor = new AuthenticatedActor(
                " subject:user-1 ", " tenant:acme ", Set.of("SCOPE_ai.invoke"));

        assertThat(actor.subjectId()).isEqualTo("subject:user-1");
        assertThat(actor.tenantId()).isEqualTo("tenant:acme");
        assertThat(actor.authorities()).containsExactly("SCOPE_ai.invoke");
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        assertThatThrownBy(() -> new AuthenticatedActor("subject user", "tenant-1", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subjectId is invalid");
    }

    @Test
    void rejectsMissingAuthorityWithStableForbiddenError() {
        AuthenticatedActor actor = new AuthenticatedActor("subject-1", "tenant-1", Set.of("SCOPE_workspace.read"));

        assertThatThrownBy(() -> actor.requireAuthority("SCOPE_ai.invoke"))
                .isInstanceOfSatisfying(dev.ainer.core.error.BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(dev.ainer.core.error.StandardErrorCode.FORBIDDEN));
    }
}
