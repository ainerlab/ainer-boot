package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.ModelCompletion;

/**
 * {@link ModelProvider} 流式回调观察者：接收增量文本与最终完成结果。
 */
public interface ModelStreamObserver {

    void onDelta(String delta);

    void onComplete(ModelCompletion completion);
}
