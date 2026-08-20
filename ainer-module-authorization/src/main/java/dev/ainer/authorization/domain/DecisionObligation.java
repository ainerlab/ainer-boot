package dev.ainer.authorization.domain;

/**
 * 附着在 ALLOW 决策上的类型化义务（ADR-0030 §6.5）。调用方必须在决策效果触达客户端
 * 之前执行每个义务。未知、不支持或执行失败的义务一律默认拒绝。S0 只实现
 * {@link PublicProjection}；其余义务类型（DataClassificationCeiling、Watermark、
 * AuthorizedUntil、RecheckBefore）在真实用例出现时再补充。
 */
public sealed interface DecisionObligation permits PublicProjection {
}
