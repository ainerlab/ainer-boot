package dev.ainer.observability;

import dev.ainer.web.request.RequestIdFilter;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTraceCorrelationFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesRequestIdToTraceIdWhenTraceAbsent() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "req-1");
        RequestTraceCorrelationFilter filter = new RequestTraceCorrelationFilter(ObservationRegistry.create());
        FilterChain chain = (request, response) ->
                assertThat(MDC.get(RequestTraceCorrelationFilter.TRACE_MDC_KEY)).isEqualTo("req-1");
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        assertThat(MDC.get(RequestTraceCorrelationFilter.TRACE_MDC_KEY)).isNull();
    }

    @Test
    void keepsExistingTraceId() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "req-2");
        MDC.put(RequestTraceCorrelationFilter.TRACE_MDC_KEY, "trace-keep");
        RequestTraceCorrelationFilter filter = new RequestTraceCorrelationFilter(ObservationRegistry.create());
        FilterChain chain = (request, response) ->
                assertThat(MDC.get(RequestTraceCorrelationFilter.TRACE_MDC_KEY)).isEqualTo("trace-keep");
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        assertThat(MDC.get(RequestTraceCorrelationFilter.TRACE_MDC_KEY)).isEqualTo("trace-keep");
    }
}
