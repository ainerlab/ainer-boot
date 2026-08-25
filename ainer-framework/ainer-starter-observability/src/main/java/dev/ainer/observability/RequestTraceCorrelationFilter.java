package dev.ainer.observability;

import dev.ainer.web.request.RequestIdFilter;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把 {@link RequestIdFilter} 已写入的 {@code requestId} 关联到 {@code traceId} MDC，
 * 并打开一条短 Observation，便于日志与 Boot ObservationRegistry 对齐。
 *
 * <p>没有外部 tracer 时用 requestId 作为 traceId，避免强制引入 OTel。
 */
public final class RequestTraceCorrelationFilter extends OncePerRequestFilter {

    public static final String TRACE_MDC_KEY = "traceId";

    private final ObservationRegistry observationRegistry;

    public RequestTraceCorrelationFilter(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        String previousTraceId = MDC.get(TRACE_MDC_KEY);
        String traceId = previousTraceId == null || previousTraceId.isBlank()
                ? requestId : previousTraceId;
        if (traceId != null) {
            MDC.put(TRACE_MDC_KEY, traceId);
        }
        Observation observation = Observation.start("ainer.request.correlation", observationRegistry);
        observation.lowCardinalityKeyValue("request.id.present", requestId != null ? "true" : "false");
        try (Observation.Scope scope = observation.openScope()) {
            filterChain.doFilter(request, response);
        } finally {
            observation.stop();
            if (previousTraceId == null) {
                MDC.remove(TRACE_MDC_KEY);
            } else {
                MDC.put(TRACE_MDC_KEY, previousTraceId);
            }
        }
    }
}
