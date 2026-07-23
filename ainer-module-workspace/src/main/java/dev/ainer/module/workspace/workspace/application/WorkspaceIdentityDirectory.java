package dev.ainer.module.workspace.workspace.application;

import dev.ainer.module.workspace.workspace.domain.SubjectId;
import dev.ainer.module.workspace.workspace.domain.TenantId;

public interface WorkspaceIdentityDirectory {

    boolean isActiveMember(TenantId tenantId, SubjectId subjectId);
}
