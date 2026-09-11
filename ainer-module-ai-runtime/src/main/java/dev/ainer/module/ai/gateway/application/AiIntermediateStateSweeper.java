package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.AiRuntimeProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 中间态定时自愈：把「超过阈值仍停在中间态」的记录推进到终态，并释放其预算占用。
 *
 * <p>覆盖三类中间态：
 * <ol>
 *   <li>{@code ainer_ai_invocation.status = 'STARTED'}——调用方线程被 kill、客户端断开、
 *       上游静默导致回写丢失时，这一行会永久停在 STARTED。当日预算按 STARTED 统计，
 *       于是该 subject 的预算被永久占用直到 UTC 跨日；</li>
 *   <li>{@code ainer_ai_task_run.status = 'RUNNING'}——AI Task 的执行体停在中间态；</li>
 *   <li>{@code ainer_ai_task.status = 'RUNNING'}——任务本身停在中间态（用 updated_at 计时，
 *       任务表没有 started_at）。</li>
 * </ol>
 *
 * <p><b>幂等与并发安全</b>：三类清扫都是「条件 UPDATE（{@code status = '中间态'}）＋
 * {@code FOR UPDATE SKIP LOCKED} 领取候选行」的集合操作，没有读-改-写窗口。多个实例同时扫，
 * 同一行只会被一个实例处理一次；重复执行同一批数据是无操作。
 *
 * <p><b>阈值安全性</b>：{@code ainer.ai.self-heal.stuck-threshold} 被强制要求大于
 * provider 的流式总超时至少 1 分钟（{@link AiRuntimeProperties#validate()}），
 * 因此清扫不会把仍在正常进行的调用判死。
 *
 * <p><b>可观测</b>：自愈数量（Counter）、超阈值积压（Gauge）、最老中间态年龄（Gauge）
 * 都会暴露给 Micrometer；没有 MeterRegistry（未引入 actuator）时自动降级为只打日志。
 */
@Component
public class AiIntermediateStateSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(AiIntermediateStateSweeper.class);

    private static final String STATE_TAG = "state";
    private static final String INVOCATION_STATE = "invocation";
    private static final String TASK_RUN_STATE = "task_run";
    private static final String TASK_STATE = "task";

    private final AiRuntimeProperties properties;
    private final AiInvocationRepository invocationRepository;
    private final AiTaskRepository taskRepository;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    private final AtomicLong stuckInvocations = new AtomicLong();
    private final AtomicLong oldestInvocationAgeSeconds = new AtomicLong();
    private final AtomicLong stuckTaskRuns = new AtomicLong();
    private final AtomicLong oldestTaskRunAgeSeconds = new AtomicLong();
    private final AtomicLong stuckTasks = new AtomicLong();
    private final AtomicLong oldestTaskAgeSeconds = new AtomicLong();

    public AiIntermediateStateSweeper(
            AiRuntimeProperties properties,
            AiInvocationRepository invocationRepository,
            AiTaskRepository taskRepository,
            Clock clock,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.properties = properties;
        this.invocationRepository = invocationRepository;
        this.taskRepository = taskRepository;
        this.clock = clock;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        if (this.meterRegistry != null) {
            registerMetrics();
        }
    }

    /**
     * 清扫周期。占位符默认值必须与 {@link AiRuntimeProperties.SelfHeal#getScanIntervalMs()} 一致，
     * 由 {@code AinerSchedulingAutoConfiguration} 提供的全局 {@code @EnableScheduling} 驱动。
     */
    @Scheduled(
            fixedDelayString = "${ainer.ai.self-heal.scan-interval-ms:60000}",
            initialDelayString = "${ainer.ai.self-heal.scan-interval-ms:60000}")
    public void sweepIntermediateStates() {
        AiRuntimeProperties.SelfHeal config = properties.getSelfHeal();
        if (!config.isEnabled()) {
            return;
        }
        Instant healedAt = clock.instant();
        Instant cutoff = healedAt.minus(config.getStuckThreshold());
        try {
            int invocations = invocationRepository.healStuckStarted(
                    cutoff, config.getBatchSize(), AiGatewayErrorCode.INVOCATION_SELF_HEALED.code(), healedAt);
            int taskRuns = taskRepository.healStuckRunningRuns(cutoff, config.getBatchSize(), healedAt);
            int tasks = taskRepository.healStuckRunningTasks(cutoff, config.getBatchSize(), healedAt);
            if (invocations + taskRuns + tasks > 0) {
                LOG.warn("AI intermediate state self-heal: invocations={}, taskRuns={}, tasks={}, "
                                + "threshold={} (healed rows are FAILED and their budget reservation is released)",
                        invocations, taskRuns, tasks, config.getStuckThreshold());
            }
            publishMetrics(cutoff, healedAt, invocations, taskRuns, tasks);
        } catch (RuntimeException exception) {
            increment("ainer.ai.intermediate_state.sweep_failed", null, 1);
            LOG.error("AI intermediate state sweep failed; the next cycle retries the same rows", exception);
        }
    }

    private void publishMetrics(Instant cutoff, Instant healedAt, int invocations, int taskRuns, int tasks) {
        if (meterRegistry == null) {
            return;
        }
        increment("ainer.ai.intermediate_state.healed", INVOCATION_STATE, invocations);
        increment("ainer.ai.intermediate_state.healed", TASK_RUN_STATE, taskRuns);
        increment("ainer.ai.intermediate_state.healed", TASK_STATE, tasks);
        stuckInvocations.set(invocationRepository.countStuckStarted(cutoff));
        oldestInvocationAgeSeconds.set(ageSeconds(invocationRepository.oldestStartedAt(), healedAt));
        stuckTaskRuns.set(taskRepository.countStuckRunningRuns(cutoff));
        oldestTaskRunAgeSeconds.set(ageSeconds(taskRepository.oldestRunningRunStartedAt(), healedAt));
        stuckTasks.set(taskRepository.countStuckRunningTasks(cutoff));
        oldestTaskAgeSeconds.set(ageSeconds(taskRepository.oldestRunningTaskUpdatedAt(), healedAt));
    }

    private void registerMetrics() {
        counter("ainer.ai.intermediate_state.healed", INVOCATION_STATE);
        counter("ainer.ai.intermediate_state.healed", TASK_RUN_STATE);
        counter("ainer.ai.intermediate_state.healed", TASK_STATE);
        counter("ainer.ai.intermediate_state.sweep_failed", null);
        gauge("ainer.ai.intermediate_state.stuck", INVOCATION_STATE, stuckInvocations);
        gauge("ainer.ai.intermediate_state.stuck", TASK_RUN_STATE, stuckTaskRuns);
        gauge("ainer.ai.intermediate_state.stuck", TASK_STATE, stuckTasks);
        gauge("ainer.ai.intermediate_state.oldest_age_seconds", INVOCATION_STATE, oldestInvocationAgeSeconds);
        gauge("ainer.ai.intermediate_state.oldest_age_seconds", TASK_RUN_STATE, oldestTaskRunAgeSeconds);
        gauge("ainer.ai.intermediate_state.oldest_age_seconds", TASK_STATE, oldestTaskAgeSeconds);
    }

    private void counter(String name, String state) {
        if (state == null) {
            meterRegistry.counter(name);
            return;
        }
        meterRegistry.counter(name, STATE_TAG, state);
    }

    private void gauge(String name, String state, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::get).tag(STATE_TAG, state).register(meterRegistry);
    }

    private void increment(String name, String state, int amount) {
        if (meterRegistry == null || amount <= 0) {
            return;
        }
        if (state == null) {
            meterRegistry.counter(name).increment(amount);
            return;
        }
        meterRegistry.counter(name, STATE_TAG, state).increment(amount);
    }

    private long ageSeconds(Instant since, Instant now) {
        return since == null ? 0 : Math.max(0, Duration.between(since, now).toSeconds());
    }
}
