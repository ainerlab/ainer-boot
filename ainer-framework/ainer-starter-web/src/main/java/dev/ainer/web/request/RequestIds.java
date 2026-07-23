package dev.ainer.web.request;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

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
