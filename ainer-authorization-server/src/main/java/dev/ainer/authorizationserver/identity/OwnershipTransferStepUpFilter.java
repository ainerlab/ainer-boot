package dev.ainer.authorizationserver.identity;

import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.autoconfigure.AinerSecurityFailureWriter;
import dev.ainer.security.error.AinerSecurityErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 所有权转移高风险端点的 step-up 门禁。见 ADR-0019 decision 25。
 *
 * <p>默认关闭。启用后，ownership-transfers 下的 POST 端点要求人员 Token 的
 * {@code amr} 含配置的强因子且 {@code auth_time} 在 {@code maxAuthAge} 内，否则返回 403
 * {@code AINER.SECURITY.RECENT_STRONG_AUTHENTICATION_REQUIRED}。
 */
public final class OwnershipTransferStepUpFilter extends OncePerRequestFilter {

    private static final String OWNERSHIP_TRANSFER_PATTERN = "/api/tenants/";
    private static final String OWNERSHIP_TRANSFER_SUFFIX = "/ownership-transfers";

    private final Set<String> requiredAmr;
    private final Duration maxAuthAge;
    private final Duration clockSkew;
    private final Clock clock;
    private final AinerSecurityFailureWriter failureWriter;

    public OwnershipTransferStepUpFilter(
            List<String> requiredAmr,
            Duration maxAuthAge,
            Duration clockSkew,
            Clock clock,
            AinerSecurityFailureWriter failureWriter) {
        this.requiredAmr = Set.copyOf(requiredAmr);
        this.maxAuthAge = maxAuthAge;
        this.clockSkew = clockSkew;
        this.clock = clock;
        this.failureWriter = failureWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isOwnershipTransferMutating(request)) {
            chain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)
                || !(jwtAuth.getPrincipal() instanceof Jwt jwt)) {
            chain.doFilter(request, response);
            return;
        }
        if (!meetsStepUp(jwt)) {
            failureWriter.write(request, response, AinerSecurityErrorCode.RECENT_STRONG_AUTHENTICATION_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isOwnershipTransferMutating(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getServletPath();
        return path != null
                && path.startsWith(OWNERSHIP_TRANSFER_PATTERN)
                && path.contains(OWNERSHIP_TRANSFER_SUFFIX);
    }

    private boolean meetsStepUp(Jwt jwt) {
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
}
