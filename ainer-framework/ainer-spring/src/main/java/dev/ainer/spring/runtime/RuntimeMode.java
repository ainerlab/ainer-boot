package dev.ainer.spring.runtime;

/**
 * Selects in-process or remote infrastructure adapters inside a concrete distribution.
 */
public enum RuntimeMode {
    MONOLITH,
    SERVICE
}
