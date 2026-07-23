package dev.ainer.module.ai.gateway.policy;

import dev.ainer.module.ai.gateway.domain.ModelMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class PromptFingerprint {

    public String digest(List<ModelMessage> messages) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ModelMessage message : messages) {
                digest.update(message.role().name().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(message.content().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0xff);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
