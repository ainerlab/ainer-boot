package dev.ainer.authorizationserver.ratelimit;

import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 按 HTTP 方法与路径对登录类端点做 IP 维度限速。见 ADR-0016。
 *
 * <p>锚定在浏览器安全链靠前位置（认证 filter 之前）。默认覆盖 {@code POST /login}、
 * {@code POST /login/webauthn} 与 {@code POST /webauthn/authenticate/options}；
 * 超额返回 429 {@code AINER.COMMON.RATE_LIMITED} + {@code Retry-After}。
 */
public final class AinerLoginRateLimitFilter extends OncePerRequestFilter {

    private static final HttpMethod POST = HttpMethod.POST;

    private final AinerRateLimiter rateLimiter;
    private final Set<String> paths;

    public AinerLoginRateLimitFilter(AinerRateLimiter rateLimiter, Set<String> paths) {
        this.rateLimiter = rateLimiter;
        this.paths = paths;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (POST.matches(request.getMethod()) && paths.contains(request.getRequestURI())) {
            AinerRateLimiter.AcquireResult result = rateLimiter.tryAcquire(clientIp(request));
            if (!result.allowed()) {
                writeRateLimited(request, response, result.retryAfterSeconds());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private void writeRateLimited(
            HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", Long.toString(Math.max(1L, retryAfterSeconds)));
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        StandardErrorCode error = StandardErrorCode.RATE_LIMITED;
        String requestId = RequestIds.currentOrCreate(request);
        String body = """
                {"code":"%s","message":"%s","requestId":"%s"}""".formatted(
                error.code(), error.defaultMessage(), requestId);
        try {
            response.getWriter().write(body);
        } catch (IOException ignored) {
            // 写入失败时已无法再做更多；保留 429 状态
        }
    }
}
