package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetRef;

import java.time.Instant;

/**
 * 面向单个主体集合族的产品侧成员关系源（ADR-0042 O2）。实现是 pull 式的：成员关系
 * 在决策时从权威事实（live 的 engagement + assignment 时段）重新计算，从不缓存，
 * 因此停用与终止在下一次决策即生效。授权核心绝不依赖任何实现。
 */
public interface SubjectSetMembershipResolver {

    /** 本解析器是否应答给定 objectType/relation 族的集合。 */
    boolean supports(String objectType, String relation);

    /**
     * 在 {@code evaluationTime} 求值请求者的成员关系。对未知对象不得抛出异常；
     * 无法读取归属事实时返回 {@code UNAVAILABLE}。
     */
    SubjectSetMembership resolve(SubjectRef requester, SubjectSetRef set, Instant evaluationTime);
}
