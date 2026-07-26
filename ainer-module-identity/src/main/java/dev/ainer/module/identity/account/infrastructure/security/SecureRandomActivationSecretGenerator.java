package dev.ainer.module.identity.account.infrastructure.security;

import dev.ainer.module.identity.account.application.ActivationSecretGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureRandomActivationSecretGenerator implements ActivationSecretGenerator {

    private static final int SECRET_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] value = new byte[SECRET_BYTES];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
