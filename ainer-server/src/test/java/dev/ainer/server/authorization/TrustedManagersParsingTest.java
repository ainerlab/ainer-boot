package dev.ainer.server.authorization;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** trusted-managers 双格式解析契约：复合键精确采用；裸 sub 绑定部署自身 issuer（1.1.0 兼容）。 */
class TrustedManagersParsingTest {

    @Test
    void compositeEntriesAreUsedVerbatim() {
        assertThat(AinerServerAuthorizationPolicyConfiguration.parseTrustedManagers(
                "https://a.example|ops, https://b.example|other", "https://local.example"))
                .containsExactlyInAnyOrder("https://a.example|ops", "https://b.example|other");
    }

    @Test
    void bareSubjectBindsToLocalIssuer() {
        assertThat(AinerServerAuthorizationPolicyConfiguration.parseTrustedManagers(
                "platform-ops", "https://local.example"))
                .containsExactly("https://local.example|platform-ops");
    }

    @Test
    void bareSubjectWithoutIssuerIsDroppedFailClosed() {
        assertThat(AinerServerAuthorizationPolicyConfiguration.parseTrustedManagers(
                "platform-ops", "")).isEmpty();
    }

    @Test
    void blankAndMalformedEntriesAreIgnored() {
        assertThat(AinerServerAuthorizationPolicyConfiguration.parseTrustedManagers(
                " , ,|nosub, | ", "https://local.example")).isEmpty();
    }

    @Test
    void mixedFormatsCoexist() {
        Set<String> parsed = AinerServerAuthorizationPolicyConfiguration.parseTrustedManagers(
                "legacy-ops, https://other.example|new-ops", "https://local.example");
        assertThat(parsed).containsExactlyInAnyOrder(
                "https://local.example|legacy-ops", "https://other.example|new-ops");
    }
}
