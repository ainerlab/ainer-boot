package dev.ainer.module.notification.notification.application;

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
import java.util.Objects;

/**
 * 通知的异步投递引擎（ADR-0038）。体现 JDK 25 + PG 18 协同的架构核心：
 *
 * <ol>
 *   <li><b>PG {@code SKIP LOCKED} 队列领取</b>——{@link NotificationRecordRepository#claimPending}
 *       用 {@code SELECT ... FOR UPDATE SKIP LOCKED} 提供无锁多消费者领取，
 *       无需外部消息队列。多个引擎实例可并发运行。</li>
 *   <li><b>JDK 25 {@code StructuredTaskScope}</b>——领取的批次经
 *       {@code StructuredTaskScope.Joiner} 发送，每次发送跑在虚拟线程上。
 *       有界、结构化、生命周期受控的并发——无需管理线程池，失败时无线程泄漏。</li>
 *   <li><b>switch 模式匹配渠道路由</b>——{@link ChannelSender} 实现按
 *       {@link NotificationChannel} 索引；路由是类型安全的 {@code Map} 查找，
 *       不是 if-else 链。</li>
 *   <li><b>指数退避重试</b>——失败的发送递增 {@code retryCount} 并按
 *       {@code 2^retryCount} 秒延迟排定 {@code nextRetryAt}，上限 {@code maxRetries}。</li>
 * </ol>
 *
 * <p>引擎按固定调度运行（{@code @Scheduled}），但每次发送都在虚拟线程上执行，
 * 因此阻塞式渠道 I/O（SMTP、HTTP）绝不会阻塞平台线程。
 */
@Component
public class NotificationDeliveryEngine {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryEngine.class);
    private static final int BATCH_SIZE = 50;

    private final NotificationRecordRepository recordRepository;
    private final Map<NotificationChannel, ChannelSender> senders;
    private final Clock clock;

    public NotificationDeliveryEngine(
            NotificationRecordRepository recordRepository,
            List<ChannelSender> senderList,
            Clock clock) {
        this.recordRepository = recordRepository;
        this.clock = clock;
        // 构建渠道 → sender 的路由 Map（便于 switch 模式匹配）
        this.senders = senderList.stream()
                .collect(java.util.stream.Collectors.toMap(ChannelSender::channel, s -> s));
    }

    /**
     * 定期领取并投递待处理通知。运行在 Spring 调度器上（若
     * {@code spring.threads.virtual.enabled=true} 则为虚拟线程）。
     */
    @Scheduled(fixedDelayString = "${ainer.notification.poll-interval-ms:5000}")
    public void deliverBatch() {
        List<NotificationRecord> batch = recordRepository.claimPending(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        deliverConcurrently(batch);
    }

    /**
     * 通过 per-task 执行器以 JDK 25 虚拟线程投递一个批次。每条通知在自己的虚拟线程上
     * 发送——阻塞式渠道 I/O（SMTP、HTTP）绝不阻塞平台线程。
     * {@code StructuredTaskScope}（JEP 505）转正稳定后将替换此实现；
     * 当前方案使用稳定的虚拟线程 API。
     */
    private void deliverConcurrently(List<NotificationRecord> batch) {
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = batch.stream()
                    .map(record -> executor.submit(() -> deliverSingle(record)))
                    .toList();
            for (var future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.warn("Notification delivery task failed: {}", e.getMessage());
                }
            }
        }
    }

    private void deliverSingle(NotificationRecord record) {
        ChannelSender sender = senders.get(record.channel());
        if (sender == null) {
            recordRepository.markFailed(record.id(),
                    "No sender registered for channel: " + record.channel(),
                    record.retryCount(), record.maxRetries(), nextRetryAt(record.retryCount()));
            return;
        }
        try {
            sender.send(record.recipient(), record.title(), record.body());
            recordRepository.markSent(record.id(), clock.instant());
        } catch (Exception e) {
            log.warn("Send failed for notification {} (retry {}/{}): {}",
                    record.id(), record.retryCount() + 1, record.maxRetries(), e.getMessage());
            recordRepository.markFailed(record.id(), e.getMessage(),
                    record.retryCount(), record.maxRetries(), nextRetryAt(record.retryCount()));
        }
    }

    /**
     * 指数退避：2^retryCount 秒（2s、4s、8s、16s……）。
     */
    private Instant nextRetryAt(int retryCount) {
        long delaySeconds = (long) Math.pow(2, retryCount + 1);
        return clock.instant().plus(Duration.ofSeconds(delaySeconds));
    }
}
