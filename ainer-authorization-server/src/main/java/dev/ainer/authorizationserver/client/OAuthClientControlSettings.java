package dev.ainer.authorizationserver.client;

import java.time.Duration;
import java.util.Set;

public record OAuthClientControlSettings(
        Set<String> operatorClientIds,
        Set<String> allowedScopes,
        Duration accessTokenTtl,
        Duration clientSecretTtl,
        int secretBytes) {
}
