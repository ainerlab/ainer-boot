package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.ModelCompletion;
import dev.ainer.module.ai.gateway.domain.ModelInvocation;

public interface ModelProvider {

    String name();

    ModelCompletion complete(ModelInvocation invocation);

    void stream(ModelInvocation invocation, ModelStreamObserver observer);
}
