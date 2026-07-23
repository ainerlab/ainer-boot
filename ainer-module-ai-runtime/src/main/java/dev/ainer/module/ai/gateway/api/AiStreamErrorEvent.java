package dev.ainer.module.ai.gateway.api;

import java.util.UUID;

public record AiStreamErrorEvent(UUID invocationId, String code, String message, String requestId) {
}
