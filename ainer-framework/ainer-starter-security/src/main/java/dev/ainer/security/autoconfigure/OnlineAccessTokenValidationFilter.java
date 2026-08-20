package dev.ainer.security.autoconfigure;

import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.error.AinerSecurityErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 高风险请求的 access token 在线校验（introspection）门禁。失败关闭（fail-closed）：
 * 仅作用于配置的受保护路径/方法；token introspection 判定为 inactive 时写出 401
 * （UNAUTHENTICATED），introspection 依赖本身失败（网络/服务不可用）时写出 503
 * （ONLINE_VALIDATION_UNAVAILABLE），绝不回退为仅验证 JWT，也不缓存 active 结果。
 */
final class OnlineAccessTokenValidationFilter extends OncePerRequestFilter {

    private static final String METRIC_PREFIX = "ainer.security.online.validation.";

    private final RequestMatcher protectedRequests;
    private final BearerTokenResolver bearerTokenResolver;
    private final OpaqueTokenIntrospector introspector;
    private final AinerSecurityFailureWriter failureWriter;
    private final Counter allowed;
    private final Counter inactive;
    private final Counter failed;
    private final Timer duration;

    OnlineAccessTokenValidationFilter(
            AinerResourceServerProperties.OnlineValidation properties,
            BearerTokenResolver bearerTokenResolver,
            OpaqueTokenIntrospector introspector,
            AinerSecurityFailureWriter failureWriter,
            MeterRegistry meterRegistry) {
        this.protectedRequests = protectedRequests(properties);
        this.bearerTokenResolver = bearerTokenResolver;
        this.introspector = introspector;
        this.failureWriter = failureWriter;
        this.allowed = counter(meterRegistry, METRIC_PREFIX + "allowed");
        this.inactive = counter(meterRegistry, METRIC_PREFIX + "inactive");
        this.failed = counter(meterRegistry, METRIC_PREFIX + "failed");
        this.duration = meterRegistry == null
                ? null
                : Timer.builder(METRIC_PREFIX + "duration").register(meterRegistry);
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
        if (authentication == null || !authentication.isAuthenticated()) {
            increment(inactive);
            SecurityContextHolder.clearContext();
            failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED);
            return;
        }
        String token = bearerTokenResolver.resolve(request);
        if (token == null) {
            increment(inactive);
            SecurityContextHolder.clearContext();
            failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED);
            return;
        }

        long startedAt = System.nanoTime();
        try {
            if (introspector.introspect(token) == null) {
                throw new OAuth2IntrospectionException("Introspection returned no principal");
            }
        } catch (BadOpaqueTokenException exception) {
            increment(inactive);
            SecurityContextHolder.clearContext();
            failureWriter.write(request, response, StandardErrorCode.UNAUTHENTICATED);
            return;
        } catch (OAuth2IntrospectionException exception) {
            increment(failed);
            SecurityContextHolder.clearContext();
            failureWriter.write(request, response, AinerSecurityErrorCode.ONLINE_VALIDATION_UNAVAILABLE);
            return;
        } catch (RuntimeException exception) {
            increment(failed);
            SecurityContextHolder.clearContext();
            failureWriter.write(request, response, AinerSecurityErrorCode.ONLINE_VALIDATION_UNAVAILABLE);
            return;
        } finally {
            if (duration != null) {
                duration.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
            }
        }
        increment(allowed);
        filterChain.doFilter(request, response);
    }

    private static RequestMatcher protectedRequests(
            AinerResourceServerProperties.OnlineValidation properties) {
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
