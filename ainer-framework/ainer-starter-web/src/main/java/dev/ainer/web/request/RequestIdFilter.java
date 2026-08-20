package dev.ainer.web.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求关联过滤器：为每个请求解析或生成 requestId，写入请求属性、{@code X-Request-Id}
 * 响应头与 MDC，保证日志与响应都可追踪同一请求。
 *
 * <p>优先复用入站 {@code X-Request-Id} 头（仅接受安全字符集且长度受限的值，防止日志
 * 注入）；否则生成新的 UUID。
 */
public final class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        request.setAttribute(RequestIds.ATTRIBUTE, requestId);
        response.setHeader(RequestIds.HEADER, requestId);

        String previousRequestId = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousRequestId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousRequestId);
            }
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String candidate = request.getHeader(RequestIds.HEADER);
        if (candidate != null) {
            candidate = candidate.trim();
            if (SAFE_REQUEST_ID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
