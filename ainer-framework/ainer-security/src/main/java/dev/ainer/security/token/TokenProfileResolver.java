package dev.ainer.security.token;

/**
 * 把已验证 access token 解析为类型化 {@link AuthenticatedPrincipal}（ADR-0030 §2.2）。
 *
 * <p>这是为 Foundation 代码解释原始 JWT claim 的唯一权威。实现（授权服务器的签发投影
 * 与 Resource Server 的请求期解析）必须：
 *
 * <ul>
 *   <li>读取 {@code token_profile} 与 {@code claim_contract_version}，遇到未知 / 缺失 /
 *       不匹配的值一律失败关闭（fail closed）；</li>
 *   <li>从 {@code iss}、{@code sub} 与 {@code actor_type} 构造带权威限定的
 *       {@link dev.ainer.security.principal.PrincipalSubjectRef}——绝不信任调用方提供的主体；</li>
 *   <li>强制 {@code USER_*} profile 解析为 Human 主体、{@code SERVICE_V1} 解析为 Service 主体；</li>
 *   <li>绝不复活 tenant 绑定语义，也绝不为 Greenfield audience 回退到旧版 profile。</li>
 * </ul>
 *
 * <p>业务模块只接收解析后的 {@code AuthenticatedPrincipal}，绝不接触原始 claims。
 */
public interface TokenProfileResolver {

    AuthenticatedPrincipal resolve(VerifiedJwtClaims claims);
}
