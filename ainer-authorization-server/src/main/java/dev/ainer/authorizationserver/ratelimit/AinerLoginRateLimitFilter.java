package dev.ainer.authorizationserver.ratelimit;

import dev.ainer.authorizationserver.login.AinerLoginPageRenderer;
import dev.ainer.authorizationserver.login.AinerLoginPageState;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.autoconfigure.AinerSecurityFailureWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 按 HTTP 方法与路径对登录类端点做 IP 维度限速。见 ADR-0016。
 *
 * <p>锚定在浏览器安全链靠前位置（认证 filter 之前）。默认覆盖 {@code POST /login}、
 * {@code POST /login/webauthn} 与 {@code POST /webauthn/authenticate/options}；
 * 超额返回 429 {@code AINER.COMMON.RATE_LIMITED} + {@code Retry-After}。
 */
public final class AinerLoginRateLimitFilter extends OncePerRequestFilter {

    private final AinerRateLimiter rateLimiter;
    private final RequestMatcher protectedRequests;
    private final AinerSecurityFailureWriter failureWriter;
    private final AinerLoginPageRenderer loginPageRenderer;
    private final Counter allowed;
    private final Counter denied;

    public AinerLoginRateLimitFilter(
            AinerRateLimiter rateLimiter,
            Set<String> paths,
            AinerSecurityFailureWriter failureWriter,
            AinerLoginPageRenderer loginPageRenderer,
            MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        List<RequestMatcher> matchers = paths.stream()
                .map(path -> PathPatternRequestMatcher.pathPattern(HttpMethod.POST, path))
                .map(RequestMatcher.class::cast)
                .toList();
        this.protectedRequests = new OrRequestMatcher(matchers);
        this.failureWriter = failureWriter;
        this.loginPageRenderer = loginPageRenderer;
        this.allowed = counter(meterRegistry, "ainer.security.login.rate-limit.allowed");
        this.denied = counter(meterRegistry, "ainer.security.login.rate-limit.denied");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (protectedRequests.matches(request)) {
            AinerRateLimiter.AcquireResult result = rateLimiter.tryAcquire(clientIp(request));
            if (!result.allowed()) {
                increment(denied);
                writeRateLimited(request, response, result.retryAfterSeconds());
                return;
            }
            increment(allowed);
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private void writeRateLimited(
            HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setHeader("Retry-After", Long.toString(Math.max(1L, retryAfterSeconds)));
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        if (isHtmlLoginSubmission(request)) {
            loginPageRenderer.render(
                    request,
                    response,
                    AinerLoginPageState.RATE_LIMITED,
                    HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        failureWriter.write(request, response, StandardErrorCode.RATE_LIMITED);
    }

    private static boolean isHtmlLoginSubmission(HttpServletRequest request) {
        if (!"/login".equals(request.getServletPath())) {
            return false;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return false;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.TEXT_HTML)
                            && !(mediaType.isWildcardType() && mediaType.isWildcardSubtype()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Counter counter(MeterRegistry meterRegistry, String name) {
        return meterRegistry == null ? null : Counter.builder(name).register(meterRegistry);
    }

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
