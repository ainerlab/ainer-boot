/**
 * Greenfield token profile 契约（ADR-0033 Greenfield §6.1、ADR-0030 §2.2）。
 *
 * <p>已验证 access token 的类型化、按 profile 限定的投影。Ainer Foundation 不再定义单一的
 * tenant 绑定 actor；每个已验证 JWT 都被解析为 {@link
 * dev.ainer.security.token.AuthenticatedPrincipal}，它把带权威限定的 {@link
 * dev.ainer.security.principal.PrincipalSubjectRef} 与封闭的 {@link
 * dev.ainer.security.token.TokenProfile}、claim 契约版本、audience、OAuth scope 上限和
 * 认证保障等级配对。旧版 profile 的 {@code tenant_id} / {@code tenant roles} claim 有意
 * 缺席；workspace 访问上限将在 {@code WorkspaceRef} 存在后的后续切片加入。
 *
 * <p>业务模块只通过 {@link dev.ainer.security.token.TokenProfileResolver} 端口消费类型化的
 * {@code AuthenticatedPrincipal}，绝不重新解析原始 JWT claim。未知 profile、缺失契约版本
 * 或 claim/profile 不匹配必须失败关闭（fail closed）。本包在 Greenfield 重置期间是增量
 * 的，尚未接入授权服务器。
 *
 * <p>包级 {@link org.jspecify.annotations.NullMarked} 声明所有类型、参数与返回值默认非空，
 * 除非显式标注 {@link org.jspecify.annotations.Nullable}。
 */
@NullMarked
package dev.ainer.security.token;

import org.jspecify.annotations.NullMarked;
