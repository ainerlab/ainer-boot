package dev.ainer.module.organization.orgdir.infrastructure;

import java.time.Instant;
import java.util.UUID;

/** 岗位在岗事实投影行。 */
public class LivePositionAssigneeRow {

    private UUID positionAssignmentId;
    private UUID engagementId;
    private Instant validUntil;

    public UUID getPositionAssignmentId() {
        return positionAssignmentId;
    }

    public void setPositionAssignmentId(UUID positionAssignmentId) {
        this.positionAssignmentId = positionAssignmentId;
    }

    public UUID getEngagementId() {
        return engagementId;
    }

    public void setEngagementId(UUID engagementId) {
        this.engagementId = engagementId;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }
}
