package dev.ainer.module.ai.gateway.application;

import java.util.UUID;

/**
 * 网关流式调用监听器：面向调用方（如 SSE 控制器）回调增量、完成与错误事件。
 */
public interface AiStreamListener {

    void onDelta(UUID invocationId, String delta);

    void onComplete(CompletionResult result);

    void onError(UUID invocationId, AiGatewayErrorCode errorCode);
}
