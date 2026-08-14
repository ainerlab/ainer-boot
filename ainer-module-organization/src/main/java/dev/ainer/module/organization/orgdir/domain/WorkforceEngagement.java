package dev.ainer.module.organization.orgdir.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Subject 在一个目录中的一段有效任职关系（ADR-0042 §2）：subject 是与 ADR-0037 一致的
 * authority 限定三元组，只允许 USER；同目录同 Subject 有效期不得重叠。
 */
public record WorkforceEngagement(
        UUID id,
        UUID workspaceId,
        UUID directoryId,
        String subjectIssuer,
        String subjectId,
        String subjectType,
        EngagementType engagementType,
        String employeeNumber,
        Instant validFrom,
        Instant validUntil,
        OrgStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public boolean coversAt(Instant evaluationTime) {
        return status == OrgStatus.ENABLED
                && !evaluationTime.isBefore(validFrom)
                && (validUntil == null || evaluationTime.isBefore(validUntil));
    }
}
