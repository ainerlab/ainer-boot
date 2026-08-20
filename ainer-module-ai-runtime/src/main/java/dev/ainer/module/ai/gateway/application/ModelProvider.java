package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.ModelCompletion;
import dev.ainer.module.ai.gateway.domain.ModelInvocation;

/**
 * 模型提供方端口：网关对底层模型服务的唯一抽象（如 OpenAI 兼容 HTTP API）。
 *
 * <p>实现负责协议适配、超时与重试；供应商错误正文不得向外透出，统一转换为
 * {@link ProviderFailure}。
 */
public interface ModelProvider {

    String name();

    ModelCompletion complete(ModelInvocation invocation);

    void stream(ModelInvocation invocation, ModelStreamObserver observer);
}
