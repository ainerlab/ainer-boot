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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        return mapper.insertReturningId(row, clock.instant());
    }

    @Override
    public Optional<NotificationRecord> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisNotificationRecordRepository::toDomain);
    }

    @Override
    public List<NotificationRecord> claimPending(int batchSize) {
        return mapper.claimPending(batchSize, clock.instant()).stream()
                .map(MybatisNotificationRecordRepository::toDomain).toList();
    }

    @Override
    public void markSent(UUID id, Instant sentAt) {
        mapper.markSent(id, sentAt, clock.instant());
    }

    @Override
    public void markFailed(UUID id, String errorMessage, int retryCount, int maxRetries, Instant nextRetryAt) {
        mapper.markFailed(id, errorMessage, retryCount, maxRetries, nextRetryAt, clock.instant());
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
        row.setNextRetryAt(record.nextRetryAt());
        row.setErrorMessage(record.errorMessage());
        row.setSentAt(record.sentAt());
        row.setCreatedAt(record.createdAt());
        row.setUpdatedAt(record.updatedAt());
        return row;
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
