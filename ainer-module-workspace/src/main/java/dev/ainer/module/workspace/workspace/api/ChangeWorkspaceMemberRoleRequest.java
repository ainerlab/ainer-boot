package dev.ainer.module.workspace.workspace.api;

import dev.ainer.module.workspace.workspace.domain.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeWorkspaceMemberRoleRequest(
        @NotBlank @Size(max = 128) String subjectId,
        @NotNull WorkspaceRole role) {
}
