package dev.ainer.cache.ratelimit;

import java.time.Duration;

/**
 * 分布式限流端口（ADR-0039 §1 第三层能力）。产品用它做跨实例共享的配额控制：AI 主体分钟配额、
 * 出站调用保护、登录/注册以外的任意「每窗口 N 次」语义。
 *
 * <p>实现：
 * <ul>
 *   <li>{@link RedisFixedWindowRateLimitPort}：Redis 固定窗口计数（默认实现，多实例共享同一计数）；</li>
 *   <li>{@link NodeLocalRateLimitPort}：进程内固定窗口（无 Redis 时的降级档，<strong>只对单实例准确</strong>）。</li>
 * </ul>
 *
 * <h2>窗口语义</h2>
 * <p><strong>固定窗口</strong>，窗口边界与 Unix epoch 对齐：窗口序号 = {@code floor(毫秒时间戳 / 窗口长度)}，
 * 因此「每分钟 60 次」的窗口是 {@code [10:00:00, 10:01:00)} 而不是「首次调用起 60 秒」。
 * 与 epoch 对齐是刻意的：多个实例不需要协商窗口起点，只要时钟大致一致就落在同一个窗口里
 * （时钟偏移的后果见下）。ADR-0039 §1 明确首版只做固定窗口，令牌桶属后续能力。
 *
 * <p>已知边界效应：固定窗口允许「窗口末尾打满 + 下一窗口开头打满」的两倍瞬时速率（这是固定窗口
 * 的固有性质，不是实现缺陷）。需要平滑速率时应引入令牌桶（后续 ADR），不要在本端口上叠加补偿逻辑。
 *
 * <h2>key 命名空间约定</h2>
 * <p>完整存储 key 由三部分组成：{@code <ainer.cache.rate-limit.key-prefix> + <调用方 key> + ":" + <窗口序号>}。
 * 例如 AI 主体限流在默认前缀下的实际 Redis key 是
 * {@code ainer:ratelimit:ai:subject:<subjectId>:<窗口序号>}。
 * <ul>
 *   <li>{@code ainer.cache.rate-limit.key-prefix} 由基础设施配置，用于多应用共享同一 Redis 时隔离命名空间；</li>
 *   <li><strong>调用方负责</strong>把业务维度写进 key，推荐 {@code {域}:{资源}:{标识}} 形式（如
 *       {@code ai:subject:abc}、{@code outbound:github-search}）；</li>
 *   <li>key 会以明文出现在 Redis 中并出现在 node-local 实现的进程内 Map 键里，<strong>不得</strong>
 *       写入秘密、token、prompt 正文、手机号等 PII；</li>
 *   <li>同一个 key 必须始终配同一组 {@code (limit, window)}：计数是共享的，中途改变 limit 会让
 *       已消耗的配额与新上限叠加（本端口不做「配置变更即重置计数」的语义）。</li>
 * </ul>
 *
 * <h2>失败时的行为（失败关闭）</h2>
 * <p>当后端（Redis）不可用、超时或返回不可解析的结果时，{@link #tryAcquire} <strong>不抛异常、
 * 也不放行</strong>，而是返回 {@link RateLimitDecision.Outcome#BACKEND_UNAVAILABLE}（{@code allowed=false}）。
 * 选择失败关闭而不是失败放行/静默降级，理由见实现类 javadoc 与
 * {@code docs/operations.md}；调用方必须按 {@link RateLimitDecision#outcome()} 区分
 * 「真的超限」与「后端不可用」，但两者都不应放行。
 *
 * <h2>公平性</h2>
 * <p>计数是「先检查后自增」的原子操作，被拒绝的请求<strong>不消耗</strong>配额（不接受惩罚性计数）。
 * 因此同一窗口内并发调用同一 key 时，放行总量恰好等于 {@code limit}，不会因为拒绝而提前耗尽。
 */
public interface RateLimitPort {

    /**
     * 尝试在固定窗口内消耗 {@code permits} 个配额。
     *
     * @param key     业务维度 key（不含基础设施前缀与窗口序号，命名约定见类 javadoc）；
     *                必须非空白
     * @param permits 本次要消耗的配额数，{@code >= 1}
     * @param limit   该 key 在窗口内允许消耗的配额总量，{@code >= 1}；由调用方（业务配置）给出，
     *                不由后端存储，因此多实例必须配置一致
     * @param window  固定窗口长度，必须为正（推荐整分钟/整秒，避免出现无法对齐的边界）
     * @return 放行/拒绝/后端不可用及剩余额度、重试等待时间；<strong>后端故障时不抛异常</strong>
     * @throws IllegalArgumentException 参数非法（key 空白、permits/limit/window 非正）
     *                                   —— 这是编程错误，不在「失败关闭」范围内
     */
    RateLimitDecision tryAcquire(String key, int permits, int limit, Duration window);

    /**
     * 该实现是否在多实例部署下给出集群精确的配额。
     *
     * <p>默认 {@code true}。进程内实现必须覆写为 {@code false}——装配层据此在启动期 WARN 并在
     * {@code AinerCacheCapabilities} 里打印 {@code clusterAccurate=false}，让「限流退化为每实例独立
     * 计数、多实例总阈值放大 N 倍」这件事显式可见，而不是静默成立（ADR-0039 落地补齐要求）。
     */
    default boolean clusterAccurate() {
        return true;
    }
}
