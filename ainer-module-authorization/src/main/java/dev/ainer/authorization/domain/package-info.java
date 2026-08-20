/**
 * Ainer 通用授权领域契约（ADR-0030）。无 Spring、无 MyBatis、不可变。包级
 * {@link org.jspecify.annotations.NullMarked} 声明所有类型、参数与返回值非空，
 * 除非显式标注 {@link org.jspecify.annotations.Nullable}。
 */
@NullMarked
package dev.ainer.authorization.domain;

import org.jspecify.annotations.NullMarked;
