package dev.ainer.authorization.domain;

import java.util.Objects;

/**
 * 集合/列表查询的授权请求（ADR-0030 §7、S3）。
 *
 * <p>与面向单个具体资源的 {@link AuthorizationRequest} 不同，该请求面向资源<em>类型</em>，
 * 要求授权引擎产出类型化查询约束（{@code Q}），由产品仓储/检索适配器应用它，在数据库
 * 层排除未授权的行。Ainer 绝不输出 SQL 字符串、表名、列名或检索 DSL——只产出由产品
 * 适配器翻译的类型化 {@code Q}。
 *
 * @param requester       发起请求的主体
 * @param accessMode      {@link AccessMode#PUBLIC_PROJECTION} 或 {@link AccessMode#AUTHENTICATED}
 * @param permission      所请求的权限（例如 {@code *.list.read}）
 * @param resourceType    被查询的资源类型
 * @param queryPurpose    稳定、人类可读的用途标签，用于审计/指标（不是原始 SQL 片段）
 * @param requestedQuery  产品定义、已完成输入校验的查询意图（例如过滤条件、排序键）
 * @param context         求值上下文（时间、保证强度、追踪）
 * @param <I>             产品查询意图类型
 */
public record QueryAuthorizationRequest<I>(
        Requester requester,
        AccessMode accessMode,
        PermissionCode permission,
        ResourceType resourceType,
        String queryPurpose,
        I requestedQuery,
        AuthorizationContext context) {

    public QueryAuthorizationRequest {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(accessMode, "accessMode");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(queryPurpose, "queryPurpose");
        Objects.requireNonNull(context, "context");
        if (queryPurpose.isBlank()) {
            throw new IllegalArgumentException("queryPurpose must not be blank");
        }
        // requestedQuery 可为 null——当产品没有额外查询意图时。
    }
}
