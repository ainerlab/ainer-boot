/**
 * Ainer 授权决策核心（ADR-0030）。{@link dev.ainer.authorization.AuthorizationService}
 * 不依赖 Spring，只消费领域契约与策略端口；持久化、Spring 与管理适配器在后续切片中以
 * 显式边界接入。包级 {@link org.jspecify.annotations.NullMarked}。
 */
@NullMarked
package dev.ainer.authorization;

import org.jspecify.annotations.NullMarked;
