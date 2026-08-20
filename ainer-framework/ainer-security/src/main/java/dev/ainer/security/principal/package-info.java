/**
 * Greenfield 类型化主体契约（ADR-0033 Greenfield §2.6、ADR-0030 §2.2）。
 *
 * <p>带权威限定、类型化的可认证主体引用，是 Foundation 的稳定安全契约：
 * {@link dev.ainer.security.principal.PrincipalSubjectRef} 总是被
 * {@link dev.ainer.security.principal.IdentityAuthorityRef} 限定，使来自不同签发方、realm
 * 或部署的相同原始 {@code sub} 值绝不冲突。只有 Human 与 Service 是凭证主体；Agent
 * 作为独立的归因引用出现（ADR-0031），刻意不属于 {@code PrincipalSubjectRef}。
 *
 * <p>包级 {@link org.jspecify.annotations.NullMarked} 声明所有类型、参数与返回值默认非空，
 * 除非显式标注 {@link org.jspecify.annotations.Nullable}。
 *
 * <p>这些契约是 Greenfield 切换后请求期主体的唯一词汇表。
 */
@NullMarked
package dev.ainer.security.principal;

import org.jspecify.annotations.NullMarked;
