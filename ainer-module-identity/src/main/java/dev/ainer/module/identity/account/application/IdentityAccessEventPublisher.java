package dev.ainer.module.identity.account.application;

import dev.ainer.module.identity.account.domain.IdentityAccessEvent;

public interface IdentityAccessEventPublisher {

    void publish(IdentityAccessEvent event);
}
