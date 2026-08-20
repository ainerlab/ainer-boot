package dev.ainer.web.request;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * 请求追踪标识（requestId）的存取入口。
 *
 * <p>约定 {@code X-Request-Id} 头与请求属性两个载体；{@link #currentOrCreate} 返回
 * 当前 requestId，缺失时生成并回填，供错误信封在过滤器之外兜底使用。
 */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = RequestIds.class.getName() + ".value";

    private RequestIds() {
    }

    public static String currentOrCreate(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            return value;
        }
        String generated = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(ATTRIBUTE, generated);
        return generated;
    }
}
