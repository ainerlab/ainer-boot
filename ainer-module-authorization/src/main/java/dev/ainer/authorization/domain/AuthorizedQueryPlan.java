package dev.ainer.authorization.domain;

import dev.ainer.authorization.AuthorizationReasonCodes;

import java.util.List;
import java.util.Objects;

/**
 * 集合查询授权的结果（ADR-0030 §7、S3）。授权引擎要么产出一个类型化查询约束 {@code Q}，
 * 由产品仓储应用它排除未授权的行，要么整体拒绝该查询。
 *
 * <p>{@code Q} 由产品定义——Ainer 不知道产品的表名、列名或检索 DSL。产品适配器把
 * {@code Q} 翻译为参数化的 PostgreSQL 或检索引擎过滤条件。解包 {@code Allowed} 却忽略
 * {@code Denied} 属于违反契约。
 *
 * @param <Q> 产品定义的类型化查询约束
 */
public sealed interface AuthorizedQueryPlan<Q> {

    /**
     * 查询已获授权。产品适配器必须把 {@code constraint} 应用到数据库/检索查询上，
     * 让未授权的行在数据层就被排除——而不是加载进 JVM 之后再过滤。
     *
     * @param constraint     产品定义的类型化约束（例如 {@code allowedWorkspaceIds, publicOnly}）
     * @param obligations    适配器必须消费的义务（例如 {@link PublicProjection}）
     * @param policyVersion  引擎的策略版本，用于审计可追溯
     */
    record Allowed<Q>(Q constraint, List<DecisionObligation> obligations, String policyVersion)
            implements AuthorizedQueryPlan<Q> {

        public Allowed {
            Objects.requireNonNull(constraint, "constraint");
            obligations = List.copyOf(Objects.requireNonNull(obligations, "obligations"));
            Objects.requireNonNull(policyVersion, "policyVersion");
        }
    }

    /**
     * 查询被拒绝。产品适配器根本不得执行该查询。
     *
     * @param reasonCode    稳定 reason code（例如 {@link AuthorizationReasonCodes#NO_BINDING}）
     * @param policyVersion 引擎的策略版本
     */
    record Denied<Q>(String reasonCode, String policyVersion) implements AuthorizedQueryPlan<Q> {

        public Denied {
            Objects.requireNonNull(reasonCode, "reasonCode");
            Objects.requireNonNull(policyVersion, "policyVersion");
        }
    }
}
