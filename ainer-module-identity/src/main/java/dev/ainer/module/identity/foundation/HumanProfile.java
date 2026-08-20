package dev.ainer.module.identity.foundation;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link HumanAccount} 的展示档案（ADR-0033 Greenfield §4，执行计划 缺口 A）。
 *
 * <p>0:1 属性聚合：一个账号至多一个档案，档案绝不能脱离账号存在。这里只存放展示级
 * 属性（显示名、头像 URL）；身份关键事实保存在账号及其 LoginIdentity 绑定上。
 */
public record HumanProfile(
        UUID accountId,
        @Nullable String displayName,
        @Nullable String avatarUrl,
        Instant updatedAt) {

    public HumanProfile {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}