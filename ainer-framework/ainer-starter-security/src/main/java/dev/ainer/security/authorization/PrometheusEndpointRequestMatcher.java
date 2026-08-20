package dev.ainer.security.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UrlPathHelper;

/** 匹配配置的 Prometheus 管理端点路径，且不依赖 Actuator 类。 */
public final class PrometheusEndpointRequestMatcher implements RequestMatcher {

    private static final String BASE_PATH_PROPERTY = "management.endpoints.web.base-path";
    private static final String PATH_MAPPING_PROPERTY =
            "management.endpoints.web.path-mapping.prometheus";

    private final String endpointPath;

    public PrometheusEndpointRequestMatcher(Environment environment) {
        this(
                environment.getProperty(BASE_PATH_PROPERTY, "/actuator"),
                environment.getProperty(PATH_MAPPING_PROPERTY, "prometheus"));
    }

    PrometheusEndpointRequestMatcher(String basePath, String pathMapping) {
        this.endpointPath = endpointPath(basePath, pathMapping);
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        String applicationPath = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return endpointPath.equals(applicationPath)
                || (endpointPath + "/").equals(applicationPath);
    }

    private static String endpointPath(String basePath, String pathMapping) {
        String normalizedBase = normalizeBasePath(basePath);
        String normalizedMapping = trimSlashes(pathMapping);
        if (normalizedMapping.isEmpty()) {
            throw new IllegalStateException("Prometheus endpoint path mapping must not be empty");
        }
        return "/".equals(normalizedBase)
                ? normalizedBase + normalizedMapping
                : normalizedBase + "/" + normalizedMapping;
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank() || "/".equals(basePath.trim())) {
            return "/";
        }
        String trimmed = trimSlashes(basePath.trim());
        return "/" + trimmed;
    }

    private static String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }
}
