package dev.ainer.module.identity.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IdentityAccessEvent(
        UUID id,
        IdentityAccessEventType type,
        UUID tenantId,
        UUID subjectId,
        int payloadVersion,
        Instant occurredAt) {

    public static final int CURRENT_PAYLOAD_VERSION = 1;

    public IdentityAccessEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        if (payloadVersion != CURRENT_PAYLOAD_VERSION) {
            throw new IllegalArgumentException("Unsupported identity access event payload version");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static IdentityAccessEvent userDisabled(
            UUID tenantId, UUID subjectId, Instant occurredAt) {
        return new IdentityAccessEvent(
                UUID.randomUUID(), IdentityAccessEventType.IDENTITY_USER_DISABLED,
                tenantId, subjectId, CURRENT_PAYLOAD_VERSION, occurredAt);
    }

    public static IdentityAccessEvent membershipRevoked(
            UUID tenantId, UUID subjectId, Instant occurredAt) {
        return new IdentityAccessEvent(
                UUID.randomUUID(), IdentityAccessEventType.IDENTITY_MEMBERSHIP_REVOKED,
                tenantId, subjectId, CURRENT_PAYLOAD_VERSION, occurredAt);
    }

    public static IdentityAccessEvent membershipRoleChanged(
            UUID tenantId, UUID subjectId, Instant occurredAt) {
        return new IdentityAccessEvent(
                UUID.randomUUID(), IdentityAccessEventType.IDENTITY_MEMBERSHIP_ROLE_CHANGED,
                tenantId, subjectId, CURRENT_PAYLOAD_VERSION, occurredAt);
    }
}
