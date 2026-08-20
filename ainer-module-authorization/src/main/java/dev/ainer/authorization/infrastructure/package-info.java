/**
 * 授权持久化切片的基础设施适配器（ADR-0030 S1）。使用 MyBatis + PostgreSQL 18 实现
 * 应用层仓储端口。包级 {@link org.jspecify.annotations.NullMarked} 声明所有类型、
 * 参数与返回值非空，除非显式标注 {@link org.jspecify.annotations.Nullable}。
 */
@NullMarked
package dev.ainer.authorization.infrastructure;

import org.jspecify.annotations.NullMarked;
