package dev.ainer.module.workspace.workspace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
        @NotBlank @Size(min = 2, max = 80) String name) {
}
