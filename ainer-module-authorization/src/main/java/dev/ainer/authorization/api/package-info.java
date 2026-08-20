/**
 * 授权模块的管理 REST API（ADR-0030 S2）。提供 Role/Binding 生命周期管理、权限目录
 * 查询与 Effective Access 查询。包级 {@link org.jspecify.annotations.NullMarked} 声明
 * 所有类型、参数与返回值非空，除非显式标注 {@link org.jspecify.annotations.Nullable}。
 */
@NullMarked
package dev.ainer.authorization.api;

import org.jspecify.annotations.NullMarked;
