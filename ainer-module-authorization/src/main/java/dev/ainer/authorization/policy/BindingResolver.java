package dev.ainer.authorization.policy;

import dev.ainer.authorization.domain.ResourceRef;
import dev.ainer.authorization.domain.SubjectBinding;
import dev.ainer.authorization.domain.SubjectRef;
import dev.ainer.authorization.domain.SubjectSetBinding;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 解析主体的 live {@link SubjectBinding}（ADR-0030 §4、§12.1）。S0 的内存实现是测试
 * 夹具；S1 用 PostgreSQL 支持的解析器替换。撤销立即生效：仍然有效的 JWT 无法恢复已
 * 撤销的数据库授权，第一版也不存在 ALLOW 缓存。
 */
public interface BindingResolver {

    Set<SubjectBinding> liveBindings(SubjectRef subject);

    /**
     * {@code at} 时刻 scope 覆盖该资源的 live 集合 Binding（ADR-0042 O2）。决策引擎还会
     * 对每个候选额外检查请求者的成员关系——主体匹配经由集合完成，而非经由该查询。
     * 默认空实现保持 S0 夹具与外部消费者源码兼容。
     */
    default List<SubjectSetBinding> liveSetBindings(ResourceRef resource, Instant at) {
        return List.of();
    }
}
