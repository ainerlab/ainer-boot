package dev.ainer.spring.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.runtime")
public class AinerRuntimeProperties {

    private RuntimeMode mode = RuntimeMode.MONOLITH;

    public RuntimeMode getMode() {
        return mode;
    }

    public void setMode(RuntimeMode mode) {
        this.mode = mode;
    }
}
