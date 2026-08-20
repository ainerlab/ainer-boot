package dev.ainer.security.autoconfigure;

import dev.ainer.security.error.AinerSecurityErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 高风险请求的 step-up 门禁。见 ADR-0017。从已验证 Bearer JWT 读取 {@code amr} 与 {@code auth_time}，
 * 要求人员 Token 携带配置的强认证因子且最近一次因子时间在 {@code max-auth-age} 内；否则失败关闭，
 * 通过 {@link AinerSecurityFailureWriter} 写出 {@code RECENT_STRONG_AUTHENTICATION_REQUIRED}（403）。
 *
 * <p>默认关闭。只在配置的受保护路径/方法上触发；非受保护请求与未认证请求不由此 filter 处理。
 */
final class RecentStrongAuthenticationFilter extends OncePerRequestFilter {

    private static final String METRIC_PREFIX = "ainer.security.step-up.";

    private final RequestMatcher protectedRequests;
    private final Set<String> requiredAmr;
    private final Duration maxAuthAge;
    private final Duration clockSkew;
    private final Clock clock;
    private final AinerSecurityFailureWriter failureWriter;
    private final Counter allowed;
    private final Counter denied;

    RecentStrongAuthenticationFilter(
            AinerResourceServerProperties.StepUp properties,
            AinerSecurityFailureWriter failureWriter,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.protectedRequests = protectedRequests(properties);
        this.requiredAmr = new HashSet<>(properties.getRequiredAmr());
        this.maxAuthAge = properties.getMaxAuthAge();
        this.clockSkew = properties.getClockSkew();
        this.clock = clock;
        this.failureWriter = failureWriter;
        this.allowed = counter(meterRegistry, METRIC_PREFIX + "allowed");
        this.denied = counter(meterRegistry, METRIC_PREFIX + "denied");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !protectedRequests.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }
        if (authentication instanceof JwtAuthenticationToken token && meetsStepUp(token.getToken())) {
            increment(allowed);
            // Publish the strong-auth verdict so the authorization context can distinguish a
            // recent strong authentication (Assurance.RECENT_STRONG) from a plain session.
            request.setAttribute(
                    "dev.ainer.security.authorization.recentStrongAuthentication", Boolean.TRUE);
            filterChain.doFilter(request, response);
            return;
        }
        increment(denied);
        failureWriter.write(request, response, AinerSecurityErrorCode.RECENT_STRONG_AUTHENTICATION_REQUIRED);
    }

    private boolean meetsStepUp(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        if (!"USER".equals(jwt.getClaimAsString("actor_type"))) {
            return false;
        }
        List<String> amr = jwt.getClaimAsStringList("amr");
        if (amr == null || !new HashSet<>(amr).containsAll(requiredAmr)) {
            return false;
        }
        Instant authTime = jwt.getClaimAsInstant("auth_time");
        if (authTime == null) {
            return false;
        }
        Instant now = clock.instant();
        if (authTime.isAfter(now.plus(clockSkew))) {
            return false;
        }
        return !authTime.plus(maxAuthAge).plus(clockSkew).isBefore(now);
    }

    private static RequestMatcher protectedRequests(AinerResourceServerProperties.StepUp properties) {
        List<RequestMatcher> matchers = new ArrayList<>();
        properties.getAlwaysProtectedPaths().stream()
                .map(PathPatternRequestMatcher::pathPattern)
                .forEach(matchers::add);
        for (String path : properties.getMutatingProtectedPaths()) {
            for (HttpMethod method : properties.getMutatingMethods()) {
                matchers.add(PathPatternRequestMatcher.pathPattern(method, path));
            }
        }
        return new OrRequestMatcher(matchers);
    }

    private static Counter counter(MeterRegistry registry, String name) {
        return registry == null ? null : Counter.builder(name).register(registry);
    }

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
