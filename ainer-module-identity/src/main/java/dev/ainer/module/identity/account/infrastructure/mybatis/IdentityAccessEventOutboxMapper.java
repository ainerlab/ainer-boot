package dev.ainer.module.identity.account.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IdentityAccessEventOutboxMapper {

    List<IdentityAccessEventRow> claimBatch(
            @Param("leaseOwner") String leaseOwner,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("maxAttempts") int maxAttempts,
            @Param("limit") int limit);

    int markPublished(
            @Param("eventId") UUID eventId,
            @Param("leaseOwner") String leaseOwner,
            @Param("publishedAt") Instant publishedAt);

    int markFailed(
            @Param("eventId") UUID eventId,
            @Param("leaseOwner") String leaseOwner,
            @Param("availableAt") Instant availableAt,
            @Param("errorCode") String errorCode);

    IdentityAccessEventOutboxStatusRow selectStatus(@Param("maxAttempts") int maxAttempts);
}
