package dev.ainer.security.token;

import java.util.Objects;

/**
 * Ainer Foundation token profile 的封闭集合（ADR-0033 Greenfield §6.1）。
 *
 * <p>每个 profile 固定 {@code sub}、{@code actor_type}、audience、scope 与可选 workspace
 * 访问上限的解释方式，并绑定一个 claim 契约版本。旧版 {@code tenant_id} / tenant-roles
 * profile 刻意不属于 Greenfield 基线；任何缺少已知 profile / 版本的 token 必须在解析时
 * 失败关闭（fail closed）。
 *
 * <p>profile 不可自由组合：USER_NEUTRAL token 绝不携带 workspace 上限，USER_WORKSPACE
 * token 必须携带，SERVICE token 绝不代表人类。线上值放在 {@code token_profile} claim 中，
 * 与 {@code claim_contract_version} 并列。
 */
public enum TokenProfile {

    USER_NEUTRAL_V1("USER_NEUTRAL_V1"),
    USER_WORKSPACE_V1("USER_WORKSPACE_V1"),
    SERVICE_V1("SERVICE_V1");

    /** 携带 profile 线上值的 claim 名称。 */
    public static final String PROFILE_CLAIM = "token_profile";

    /** 携带 claim 契约版本的 claim 名称。 */
    public static final String CONTRACT_VERSION_CLAIM = "claim_contract_version";

    /** Greenfield 基线的当前 claim 契约版本。 */
    public static final String CURRENT_CONTRACT_VERSION = "1";

    private final String claimValue;

    TokenProfile(String claimValue) {
        this.claimValue = claimValue;
    }

    /** 写入 {@code token_profile} claim 的规范化线上值。 */
    public String claimValue() {
        return claimValue;
    }

    /**
     * 从线上值解析 profile。失败关闭（fail closed）：空白或未知值直接抛异常，
     * 因此解析器绝不会静默接受未标注 profile 的 token。
     */
    public static TokenProfile fromClaim(String value) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        for (TokenProfile profile : values()) {
            if (profile.claimValue.equals(trimmed)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown token_profile claim: " + value);
    }
}
