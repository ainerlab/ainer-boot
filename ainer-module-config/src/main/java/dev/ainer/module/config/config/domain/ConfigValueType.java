package dev.ainer.module.config.config.domain;

/**
 * Supported value types for dynamic configuration (ADR-0038). The type determines how the raw string
 * value is parsed and type-safely retrieved.
 */
public enum ConfigValueType {
    STRING,
    INTEGER,
    LONG,
    DECIMAL,
    BOOLEAN,
    JSON
}
