package dev.ainer.module.notification.notification.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ainer.module.notification.notification.application.NotificationRecordRepository;
import dev.ainer.module.notification.notification.domain.NotificationChannel;
import dev.ainer.module.notification.notification.domain.NotificationRecord;
import dev.ainer.module.notification.notification.domain.NotificationStatus;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link NotificationRecordRepository} 的 MyBatis 适配器，对应表 {@code ainer_notification_record}。
 * 队列领取（SKIP LOCKED）、发送结果回写与运维分页由对应 mapper 语句完成；
 * payload 在 JSONB 与 Map 之间双向转换，解析失败按空值降级。
 */
@Repository
public class MybatisNotificationRecordRepository implements NotificationRecordRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final NotificationRecordMapper mapper;
    private final Clock clock;

    public MybatisNotificationRecordRepository(NotificationRecordMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public UUID save(NotificationRecord record) {
        NotificationRecordRow row = toRow(record);
        return mapper.insertReturningId(row, micros(clock.instant()));
    }

    @Override
    public Optional<NotificationRecord> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisNotificationRecordRepository::toDomain);
    }

    @Override
    public List<NotificationRecord> claimPending(int batchSize, String leaseOwner, Instant leaseExpiresAt) {
        return mapper.claimPending(batchSize, leaseOwner, micros(leaseExpiresAt), micros(clock.instant())).stream()
                .map(MybatisNotificationRecordRepository::toDomain).toList();
    }

    @Override
    public void markSent(UUID id, String leaseOwner, Instant sentAt) {
        mapper.markSent(id, leaseOwner, micros(sentAt), micros(clock.instant()));
    }

    @Override
    public void markFailed(UUID id, String leaseOwner, String errorMessage,
            int retryCount, int maxRetries, Instant nextRetryAt) {
        mapper.markFailed(id, leaseOwner, errorMessage, retryCount, maxRetries,
                micros(nextRetryAt), micros(clock.instant()));
    }

    @Override
    public dev.ainer.module.notification.notification.application.NotificationPageSlice<NotificationRecord> findPage(
            @org.jspecify.annotations.Nullable String status, long offset, int size) {
        List<NotificationRecord> items = mapper.selectPage(status, offset, size).stream()
                .map(MybatisNotificationRecordRepository::toDomain).toList();
        return new dev.ainer.module.notification.notification.application.NotificationPageSlice<>(
                items, mapper.countPage(status));
    }

    private static NotificationRecordRow toRow(NotificationRecord record) {
        NotificationRecordRow row = new NotificationRecordRow();
        row.setId(record.id());
        row.setTemplateCode(record.templateCode());
        row.setChannel(record.channel().name());
        row.setRecipient(record.recipient());
        row.setTitle(record.title());
        row.setBody(record.body());
        row.setPayload(toJson(record.payload()));
        row.setStatus(record.status().name());
        row.setRetryCount(record.retryCount());
        row.setMaxRetries(record.maxRetries());
        row.setNextRetryAt(micros(record.nextRetryAt()));
        row.setErrorMessage(record.errorMessage());
        row.setSentAt(micros(record.sentAt()));
        row.setCreatedAt(micros(record.createdAt()));
        row.setUpdatedAt(micros(record.updatedAt()));
        return row;
    }

    /**
     * PostgreSQL {@code timestamptz} 是微秒精度，而 {@link Instant} 是纳秒精度：不截断时同一时刻
     * 「内存里的值」与「读回来的值」不相等（本地纳秒末位恰好为 0 时不会暴露，CI 上会）。按仓库既有的
     * 时间入口约定（task、organization、knowledge 模块同款处理），在持久化边界统一截断到微秒，
     * 保证内存值与落库值一致。
     */
    private static Instant micros(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    private static NotificationRecord toDomain(NotificationRecordRow row) {
        return new NotificationRecord(
                row.getId(), row.getTemplateCode(), NotificationChannel.valueOf(row.getChannel()),
                row.getRecipient(), row.getTitle(), row.getBody(), fromJson(row.getPayload()),
                NotificationStatus.valueOf(row.getStatus()), row.getRetryCount(), row.getMaxRetries(),
                row.getNextRetryAt(), row.getErrorMessage(), row.getSentAt(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private static String toJson(Map<String, Object> map) {
        if (map == null) { return null; }
        try { return JSON.writeValueAsString(map); }
        catch (Exception e) { return null; }
    }

    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) { return null; }
        try { return JSON.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return null; }
    }
}
