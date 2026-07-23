package dev.ainer.module.ai.gateway.application;

import java.util.UUID;

public interface AiStreamListener {

    void onDelta(UUID invocationId, String delta);

    void onComplete(CompletionResult result);

    void onError(UUID invocationId, AiGatewayErrorCode errorCode);
}
