package dev.ainer.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 观测 Starter 配置（ADR-0029 T1#6）：默认启用 Observation 与 requestId/traceId
 * 关联；OTLP 导出默认关闭，开启只装配标记，不强制拉全链路导出器。
 */
@ConfigurationProperties(prefix = "ainer.observability")
public class AinerObservabilityProperties {

    private final boolean enabled;
    private final Otlp otlp;

    public AinerObservabilityProperties(Boolean enabled, Otlp otlp) {
        this.enabled = enabled == null || enabled;
        this.otlp = otlp == null ? new Otlp(false, "") : otlp;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Otlp getOtlp() {
        return otlp;
    }

    public record Otlp(Boolean enabled, String endpoint) {

        public Otlp {
            enabled = enabled != null && enabled;
            endpoint = endpoint == null ? "" : endpoint;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
