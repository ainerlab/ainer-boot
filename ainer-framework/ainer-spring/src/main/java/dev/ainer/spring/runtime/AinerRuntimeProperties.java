package dev.ainer.spring.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.runtime")
public class AinerRuntimeProperties {

    private final RuntimeMode mode;

    public AinerRuntimeProperties(RuntimeMode mode) {
        this.mode = mode != null ? mode : RuntimeMode.MONOLITH;
    }

    public RuntimeMode getMode() {
        return mode;
    }
}
