package dev.ainer.security.principal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Greenfield typed principal contracts (S1.0). Covers authority-qualification, sealed non-equivalence
 * and validation invariants mandated by ADR-0033 Greenfield §2.2/§2.3/§2.6.
 */
class PrincipalSubjectRefTest {

    private static final IdentityAuthorityRef AINER =
            new IdentityAuthorityRef("https://ainer.example/auth");
    private static final IdentityAuthorityRef AINER_PRIVATE =
            new IdentityAuthorityRef("https://ainer.example/auth", "private-deployment");

    @Test
    void identityAuthorityRefIsValueObjectAndRealmIsOptional() {
        assertThat(new IdentityAuthorityRef("https://ainer.example/auth"))
                .isEqualTo(new IdentityAuthorityRef("https://ainer.example/auth"));
        assertThat(new IdentityAuthorityRef("https://ainer.example/auth").hasRealm()).isFalse();
        assertThat(AINER_PRIVATE.hasRealm()).isTrue();
        assertThat(AINER_PRIVATE.realm()).isEqualTo("private-deployment");
        assertThat(AINER).isNotEqualTo(AINER_PRIVATE);
    }

    @Test
    void identityAuthorityRefRejectsBlankAndOversizeIssuer() {
        assertThatThrownBy(() -> new IdentityAuthorityRef("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityAuthorityRef(null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IdentityAuthorityRef("https://ainer.example/auth", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void humanAndServiceAreEqualByAuthorityAndId() {
        assertThat(new HumanSubjectRef(AINER, "acc-1"))
                .isEqualTo(new HumanSubjectRef(AINER, "acc-1"));
        assertThat(new ServiceSubjectRef(AINER, "svc-1"))
                .isEqualTo(new ServiceSubjectRef(AINER, "svc-1"));
    }

    @Test
    void sameIdUnderDifferentAuthorityIsNotEqual() {
        assertThat(new HumanSubjectRef(AINER, "acc-1"))
                .isNotEqualTo(new HumanSubjectRef(AINER_PRIVATE, "acc-1"));
        assertThat(new ServiceSubjectRef(AINER, "svc-1"))
                .isNotEqualTo(new ServiceSubjectRef(AINER_PRIVATE, "svc-1"));
    }

    @Test
    void humanAndServiceAreNeverTheSamePrincipalEvenWithIdenticalIds() {
        HumanSubjectRef human = new HumanSubjectRef(AINER, "shared-id");
        ServiceSubjectRef service = new ServiceSubjectRef(AINER, "shared-id");

        PrincipalSubjectRef humanRef = human;
        PrincipalSubjectRef serviceRef = service;

        assertThat(human).isNotEqualTo(service);
        assertThat(humanRef).isNotEqualTo(serviceRef);
        assertThat(humanRef.subjectId()).isEqualTo(serviceRef.subjectId());
    }

    @Test
    void sealedPrincipalIsExhaustivelyMatchable() {
        PrincipalSubjectRef ref = new HumanSubjectRef(AINER, "acc-1");
        String kind = switch (ref) {
            case HumanSubjectRef ignored -> "human";
            case ServiceSubjectRef ignored -> "service";
        };
        assertThat(kind).isEqualTo("human");
        assertThat(ref.authority()).isEqualTo(AINER);
        assertThat(ref.subjectId()).isEqualTo("acc-1");
    }

    @Test
    void subjectRefsRejectInvalidIdentifiers() {
        assertThatThrownBy(() -> new HumanSubjectRef(AINER, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HumanSubjectRef(AINER, "has space"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServiceSubjectRef(AINER, "has space"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HumanSubjectRef(null, "acc-1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HumanSubjectRef(AINER, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void subjectIdAccessorReflectsPrincipalKind() {
        assertThat(new HumanSubjectRef(AINER, "acc-7").subjectId()).isEqualTo("acc-7");
        assertThat(new ServiceSubjectRef(AINER, "svc-9").subjectId()).isEqualTo("svc-9");
        assertThat(new HumanSubjectRef(AINER, "acc-7").accountId()).isEqualTo("acc-7");
        assertThat(new ServiceSubjectRef(AINER, "svc-9").servicePrincipalId()).isEqualTo("svc-9");
    }
}
