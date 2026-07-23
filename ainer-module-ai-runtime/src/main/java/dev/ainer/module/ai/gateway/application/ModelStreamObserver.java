package dev.ainer.module.ai.gateway.application;

import dev.ainer.module.ai.gateway.domain.ModelCompletion;

public interface ModelStreamObserver {

    void onDelta(String delta);

    void onComplete(ModelCompletion completion);
}
