package dev.ainer.security.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusEndpointRequestMatcherTest {

    @Test
    void matchesDefaultEndpointOnly() {
        PrometheusEndpointRequestMatcher matcher =
                new PrometheusEndpointRequestMatcher("/actuator", "prometheus");

        assertThat(matcher.matches(request("", "/actuator/prometheus"))).isTrue();
        assertThat(matcher.matches(request("", "/actuator/prometheus/"))).isTrue();
        assertThat(matcher.matches(request("", "/actuator/health"))).isFalse();
        assertThat(matcher.matches(request("", "/actuator/prometheus/extra"))).isFalse();
    }

    @Test
    void followsCustomBasePathMappingAndContextPath() {
        PrometheusEndpointRequestMatcher matcher =
                new PrometheusEndpointRequestMatcher("/management/", "/metrics");

        assertThat(matcher.matches(request("/ainer", "/ainer/management/metrics"))).isTrue();
        assertThat(matcher.matches(request("/ainer", "/ainer/management/%6Detrics"))).isTrue();
        assertThat(matcher.matches(request("/ainer", "/ainer/actuator/prometheus"))).isFalse();
    }

    private MockHttpServletRequest request(String contextPath, String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setContextPath(contextPath);
        request.setRequestURI(requestUri);
        return request;
    }
}
