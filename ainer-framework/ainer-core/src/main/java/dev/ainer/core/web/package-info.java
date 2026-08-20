/**
 * 稳定的 web 响应契约。包级 {@link org.jspecify.annotations.NullMarked} 声明所有类型、
 * 方法参数与返回值默认非空，除非显式标注 {@link org.jspecify.annotations.Nullable}；
 * 失败响应的 {@code data} 载荷显式可为 null。
 */
@NullMarked
package dev.ainer.core.web;

import org.jspecify.annotations.NullMarked;
