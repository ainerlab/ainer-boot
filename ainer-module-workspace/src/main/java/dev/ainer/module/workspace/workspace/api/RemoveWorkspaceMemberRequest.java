package dev.ainer.module.workspace.workspace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemoveWorkspaceMemberRequest(
        @NotBlank @Size(max = 128) String subjectId) {
}
