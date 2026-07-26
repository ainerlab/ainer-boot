package dev.ainer.security.autoconfigure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecentStrongAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-25T00:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AinerSecurityFailureWriter failureWriter =
            new AinerSecurityFailureWriter(new tools.jackson.databind.json.JsonMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsWhenAmrContainsRequiredFactorAndAuthTimeIsFresh() throws Exception {
        RecentStrongAuthenticationFilter filter = filter(Duration.ofMinutes(15), List.of("mfa"));
        assertThat(call(filter, token(List.of("pwd", "mfa", "pop"), NOW), "POST", "/api/sensitive/x"))
                .isTrue();
    }

    @Test
    void deniesWhenPasswordOnlyTokenLacksMfa() throws Exception {
        RecentStrongAuthenticationFilter filter = filter(Duration.ofMinutes(15), List.of("mfa"));
        SecurityContextHolder.getContext().setAuthentication(token(List.of("pwd"), NOW));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/sensitive/x"), response, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("RECENT_STRONG_AUTHENTICATION_REQUIRED");
    }

    @Test
    void leavesAnonymousRequestToAuthenticationEntryPoint() throws Exception {
        RecentStrongAuthenticationFilter filter = filter(Duration.ofMinutes(15), List.of("mfa"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/sensitive/x"), response, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void deniesWhenAuthTimeIsOlderThanMaxAge() throws Exception {
        RecentStrongAuthenticationFilter filter = filter(Duration.ofMinutes(15), List.of("mfa"));
        assertThat(call(filter, token(List.of("mfa"), NOW.minusSeconds(60 * 20)), "POST", "/api/sensitive/x"))
                .isFalse();
    }

    @Test
    void deniesWhenAuthTimeMissingOrAmrMissing() throws Exception {
        RecentStrongAuthenticationFilter filter = filter(Duration.ofMinutes(15), List.of("mfa"));
        assertThat(call(filter, token(List.of("mfa"), null), "POST", "/api/sensitive/x")).isFalse();
        assertThat(call(filter, token(null, NOW), "POST", "/api/sensitive/x")).isFalse();
    }

    @Test
    void deniesServiceTokenAndAuthTimeBeyondClockSkew() throws Exception {
        RecentStrongAuthenticationFilter filter = filter(Duration.ofMinutes(15), List.of("mfa"));
        assertThat(call(
                filter,
                token("SERVICE", List.of("mfa"), NOW),
                "POST",
                "/api/sensitive/x")).isFalse();
        assertThat(call(
                filter,
                token("USER", List.of("mfa"), NOW.plusSeconds(61)),
                "POST",
                "/api/sensitive/x")).isFalse();
    }

    @Test
    void deniesNonJwtAuthentication() throws Exception {
        RecentStrongAuthenticationFilter filter = filter(Duration.ofMinutes(15), List.of("mfa"));
        Authentication authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "user", "creds", java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/sensitive/x"), response, chain);
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void skipsNonProtectedPath() throws Exception {
        RecentStrongAuthenticationFilter filter = filter(Duration.ofMinutes(15), List.of("mfa"));
        SecurityContextHolder.getContext().setAuthentication(token(List.of("pwd"), NOW));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("GET", "/api/open"), response, chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void stepUpPropertiesValidateRejectsInvalidConfiguration() {
        AinerResourceServerProperties.StepUp empty = new AinerResourceServerProperties.StepUp();
        empty.setEnabled(true);
        assertThatThrownBy(empty::validate).isInstanceOf(IllegalStateException.class);

        AinerResourceServerProperties.StepUp noAmr = new AinerResourceServerProperties.StepUp();
        noAmr.setEnabled(true);
        noAmr.setAlwaysProtectedPaths(List.of("/api/x"));
        noAmr.setRequiredAmr(List.of());
        assertThatThrownBy(noAmr::validate).isInstanceOf(IllegalStateException.class);

        AinerResourceServerProperties.StepUp excessiveSkew = new AinerResourceServerProperties.StepUp();
        excessiveSkew.setEnabled(true);
        excessiveSkew.setAlwaysProtectedPaths(List.of("/api/x"));
        excessiveSkew.setClockSkew(Duration.ofMinutes(6));
        assertThatThrownBy(excessiveSkew::validate).isInstanceOf(IllegalStateException.class);

        AinerResourceServerProperties.StepUp tooOld = new AinerResourceServerProperties.StepUp();
        tooOld.setEnabled(true);
        tooOld.setAlwaysProtectedPaths(List.of("/api/x"));
        tooOld.setMaxAuthAge(Duration.ofHours(25));
        assertThatThrownBy(tooOld::validate).isInstanceOf(IllegalStateException.class);
    }

    private RecentStrongAuthenticationFilter filter(Duration maxAuthAge, List<String> requiredAmr) {
        AinerResourceServerProperties.StepUp properties = new AinerResourceServerProperties.StepUp();
        properties.setEnabled(true);
        properties.setMaxAuthAge(maxAuthAge);
        properties.setRequiredAmr(requiredAmr);
        properties.setAlwaysProtectedPaths(List.of("/api/sensitive/**"));
        return new RecentStrongAuthenticationFilter(properties, failureWriter, null, CLOCK);
    }

    private static boolean call(
            RecentStrongAuthenticationFilter filter, Authentication authentication,
            String method, String uri) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request(method, uri), response, chain);
        return chain.getRequest() != null;
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod(method);
        servletRequest.setRequestURI(uri);
        return servletRequest;
    }

    private static JwtAuthenticationToken token(List<String> amr, Instant authTime) {
        return token("USER", amr, authTime);
    }

    private static JwtAuthenticationToken token(String actorType, List<String> amr, Instant authTime) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.parse("2026-07-25T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-25T01:00:00Z"))
                .claim("actor_type", actorType);
        if (amr != null) {
            builder.claim("amr", amr);
        }
        if (authTime != null) {
            builder.claim("auth_time", authTime);
        }
        return new JwtAuthenticationToken(builder.build(), java.util.List.of());
    }
}
