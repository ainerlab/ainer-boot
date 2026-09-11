package dev.ainer.module.notification.notification.application;

import dev.ainer.core.uuid.Uuidv7;
import dev.ainer.module.notification.notification.domain.ChannelSender;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 通知的异步投递引擎（ADR-0038）。体现 JDK 25 + PG 18 协同的架构核心：
 *
 * <ol>
 *   <li><b>PG {@code SKIP LOCKED} 队列领取</b>——{@link NotificationRecordRepository#claimPending}
 *       用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 提供无锁多消费者领取，
 *       无需外部消息队列。多个引擎实例可并发运行。</li>
 *   <li><b>领取租约</b>——领取时写入 {@code lease_owner}/{@code lease_expires_at}：
 *       租约未过期的 {@code SENDING} 记录不会被再次领取，因此发送慢于
 *       {@code ainer.notification.poll-interval-ms} 也不会重复投递；租约过期后允许重新
 *       领取，覆盖实例崩溃或发送线程卡死。写回结果按 {@code (status='SENDING', lease_owner)}
 *       做 CAS，租约被接管后旧领取者的迟到写回是空操作。</li>
 *   <li><b>JDK 25 虚拟线程</b>——每次发送跑在虚拟线程上，阻塞式渠道 I/O（SMTP、HTTP）
 *       绝不占用平台线程；批次等待有界超时（{@code ainer.notification.delivery.send-timeout}），
 *       超时按失败处理走既有重试/终态语义，调度线程不会被卡死的发送永久挂住。</li>
 *   <li><b>switch 模式匹配渠道路由</b>——{@link ChannelSender} 实现按
 *       {@link NotificationChannel} 索引；路由是类型安全的 {@code Map} 查找，
 *       不是 if-else 链。</li>
 *   <li><b>指数退避重试</b>——失败的发送递增 {@code retryCount} 并按
 *       {@code 2^retryCount} 秒延迟排定 {@code nextRetryAt}，上限 {@code maxRetries}。</li>
 * </ol>
 */
@Component
public class NotificationDeliveryEngine {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryEngine.class);
    private static final int BATCH_SIZE = 50;

    private final NotificationRecordRepository recordRepository;
    private final Map<NotificationChannel, ChannelSender> senders;
    private final Clock clock;
    private final NotificationDeliveryProperties properties;

    /** 实例标识：写入 {@code lease_owner}，用于写回结果的 CAS 与运维定位。 */
    private final String leaseOwner = "notification-engine-" + Uuidv7.generate();

    public NotificationDeliveryEngine(
            NotificationRecordRepository recordRepository,
            List<ChannelSender> senderList,
            Clock clock,
            NotificationDeliveryProperties properties) {
        this.recordRepository = recordRepository;
        this.clock = clock;
        this.properties = properties;
        // 构建渠道 → sender 的路由 Map（便于 switch 模式匹配）
        this.senders = senderList.stream()
                .collect(java.util.stream.Collectors.toMap(ChannelSender::channel, s -> s));
    }

    /**
     * 定期领取并投递待处理通知。运行在 Spring 调度器上（若
     * {@code spring.threads.virtual.enabled=true} 则为虚拟线程）。
     *
     * <p>调度由 {@code AinerSchedulingAutoConfiguration} 提供的全局
     * {@code @EnableScheduling} 驱动（默认生效，见 {@code ainer.scheduling.enabled}）；
     * 缺少该装配时本方法不会被调用，通知会静默停留在 PENDING。
     */
    @Scheduled(fixedDelayString = "${ainer.notification.poll-interval-ms:5000}")
    public void deliverBatch() {
        List<NotificationRecord> batch = recordRepository.claimPending(
                BATCH_SIZE, leaseOwner, clock.instant().plus(properties.leaseDuration()));
        if (batch.isEmpty()) {
            return;
        }
        deliverConcurrently(batch);
    }

    /**
     * 通过 per-task 执行器以虚拟线程并发投递一个批次——阻塞式渠道 I/O（SMTP、HTTP）
     * 绝不阻塞平台线程。
     *
     * <p>等待有界：批次共享一个 {@code send-timeout} 截止时间，逐条 {@link Future#get}
     * 只等待剩余时间，超过截止时间的记录按失败处理（进入重试/终态），调度线程不会被
     * 「不返回也不抛异常」的上游永久挂住。执行器用 {@code shutdownNow()} 收尾而不是
     * try-with-resources——{@code close()} 会无限等待未结束的任务。
     */
    private void deliverConcurrently(List<NotificationRecord> batch) {
        Instant deadline = clock.instant().plus(properties.sendTimeout());
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<? extends Future<?>> futures = batch.stream()
                    .map(record -> executor.submit(() -> deliverSingle(record)))
                    .toList();
            for (int index = 0; index < batch.size(); index++) {
                NotificationRecord record = batch.get(index);
                long remainingMillis = Duration.between(clock.instant(), deadline).toMillis();
                if (remainingMillis <= 0) {
                    markTimedOut(record);
                    continue;
                }
                try {
                    futures.get(index).get(remainingMillis, TimeUnit.MILLISECONDS);
                } catch (TimeoutException exception) {
                    markTimedOut(record);
                } catch (ExecutionException exception) {
                    log.warn("Notification delivery task failed: {}",
                            exception.getCause() == null
                                    ? exception.getMessage()
                                    : exception.getCause().getMessage());
                } catch (InterruptedException exception) {
                    // 调度线程被要求停止：不再等待剩余任务，未回写的记录由租约过期后重新领取。
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void deliverSingle(NotificationRecord record) {
        ChannelSender sender = senders.get(record.channel());
        if (sender == null) {
            recordRepository.markFailed(record.id(), leaseOwner,
                    "No sender registered for channel: " + record.channel(),
                    record.retryCount(), record.maxRetries(), nextRetryAt(record.retryCount()));
            return;
        }
        try {
            sender.send(record.recipient(), record.title(), record.body());
            recordRepository.markSent(record.id(), leaseOwner, clock.instant());
        } catch (Exception e) {
            log.warn("Send failed for notification {} (retry {}/{}): {}",
                    record.id(), record.retryCount() + 1, record.maxRetries(), e.getMessage());
            recordRepository.markFailed(record.id(), leaseOwner, e.getMessage(),
                    record.retryCount(), record.maxRetries(), nextRetryAt(record.retryCount()));
        }
    }

    /**
     * 超时的发送按失败处理：写入错误信息并按既有重试语义回到 PENDING 或终态 FAILED。
     * 发送线程本身无法被强制终止（SMTP/HTTP 可能不响应中断），但它持有的租约会被清空，
     * 状态也由租约 CAS 保护——迟到完成的写回不会覆盖后续领取者的结果。
     */
    private void markTimedOut(NotificationRecord record) {
        long timeoutMillis = properties.sendTimeout().toMillis();
        log.warn("Send timed out for notification {} after {}ms (retry {}/{})",
                record.id(), timeoutMillis, record.retryCount() + 1, record.maxRetries());
        recordRepository.markFailed(record.id(), leaseOwner,
                "Delivery timed out after " + timeoutMillis + "ms",
                record.retryCount(), record.maxRetries(), nextRetryAt(record.retryCount()));
    }

    /**
     * 指数退避：2^retryCount 秒（2s、4s、8s、16s……）。
     */
    private Instant nextRetryAt(int retryCount) {
        long delaySeconds = (long) Math.pow(2, retryCount + 1);
        return clock.instant().plus(Duration.ofSeconds(delaySeconds));
    }
}
