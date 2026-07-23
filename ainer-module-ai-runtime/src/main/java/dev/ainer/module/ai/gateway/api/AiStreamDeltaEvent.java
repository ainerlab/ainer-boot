package dev.ainer.module.ai.gateway.api;

import java.util.UUID;

public record AiStreamDeltaEvent(UUID invocationId, String delta) {
}
