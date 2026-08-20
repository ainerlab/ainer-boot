/**
 * Greenfield Identity foundation 领域（ADR-0033 Greenfield §3-§5，重置影响 §4.1）。
 *
 * <p>{@link dev.ainer.module.identity.foundation.HumanAccount} 是由
 * {@link dev.ainer.security.principal.IdentityAuthorityRef} 限定的自然人安全账号生命周期根，
 * 与 {@link dev.ainer.module.identity.foundation.LoginIdentity} 条目为 1:N 绑定。账号、凭证、
 * 档案与 ServicePrincipal 类型是 Identity foundation 唯一的运行时模型。
 *
 * <p>包级 {@link org.jspecify.annotations.NullMarked} 声明所有类型、参数与返回值默认非空，
 * 除非显式标注 {@link org.jspecify.annotations.Nullable}。
 */
@NullMarked
package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.NullMarked;
