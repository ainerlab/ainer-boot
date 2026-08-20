package dev.ainer.module.config.config.domain;

/**
 * 动态配置支持的值类型（ADR-0038）。类型决定原始字符串如何被解析
 * 并以类型安全的方式读取。
 */
public enum ConfigValueType {
    STRING,
    INTEGER,
    LONG,
    DECIMAL,
    BOOLEAN,
    JSON
}
