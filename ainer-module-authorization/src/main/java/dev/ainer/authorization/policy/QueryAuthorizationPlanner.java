package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.AuthorizedQueryPlan;
import dev.ainer.authorization.domain.QueryAuthorizationRequest;

/**
 * 为集合/列表查询产出类型化 {@link AuthorizedQueryPlan}（ADR-0030 §7、S3）。
 *
 * <p>这是 {@link dev.ainer.authorization.AuthorizationService#authorize} 的查询级对应物。
 * 规划器不是对单个具体资源做决策，而是求值请求者的 Binding 与 scope ceiling，产出一个
 * 类型化约束 {@code Q}，由产品仓储/检索适配器应用到数据库查询上。
 *
 * <p>Ainer 自身的实现处理 scope ceiling、Binding 聚合与授权路径分流。产品模块提供自己的
 * {@code I}（查询意图）与 {@code Q}（约束）类型；由它们把 {@code Q} 翻译为参数化 SQL
 * 或检索过滤条件——Ainer 绝不输出 SQL。
 *
 * @param <I> 产品定义的查询意图类型（已完成输入校验）
 * @param <Q> 产品定义的类型化查询约束（由仓储/检索适配器应用）
 */
public interface QueryAuthorizationPlanner<I, Q> {

    AuthorizedQueryPlan<Q> plan(QueryAuthorizationRequest<I> request);
}
