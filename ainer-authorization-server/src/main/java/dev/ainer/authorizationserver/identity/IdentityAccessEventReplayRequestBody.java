package dev.ainer.authorizationserver.identity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record IdentityAccessEventReplayRequestBody(
        @NotNull UUID eventId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._:@/-]{1,128}") String incidentReference) {
}
