package dev.ainer.module.identity.account.infrastructure.security;

import dev.ainer.module.identity.account.application.ProtectedTenantProvisioningNotification;
import dev.ainer.module.identity.account.application.TenantProvisioningNotification;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPayloadProtector;
import dev.ainer.module.identity.account.application.TenantProvisioningNotificationType;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class AesGcmTenantProvisioningNotificationPayloadProtector
        implements TenantProvisioningNotificationPayloadProtector {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_STRING_BYTES = 1024;
    private static final Pattern KEY_VERSION = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private final String activeKeyVersion;
    private final Map<String, SecretKeySpec> keys;
    private final SecureRandom secureRandom;

    public AesGcmTenantProvisioningNotificationPayloadProtector(
            String activeKeyVersion,
            Map<String, byte[]> keyRing,
            SecureRandom secureRandom) {
        if (activeKeyVersion == null || !KEY_VERSION.matcher(activeKeyVersion).matches()) {
            throw new IllegalArgumentException("Active notification key version is invalid");
        }
        Objects.requireNonNull(keyRing, "keyRing");
        LinkedHashMap<String, SecretKeySpec> validated = new LinkedHashMap<>();
        keyRing.forEach((version, key) -> {
            if (version == null || !KEY_VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException("Notification key version is invalid");
            }
            byte[] material = Objects.requireNonNull(key, "key").clone();
            if (material.length != 32) {
                throw new IllegalArgumentException(
                        "Notification protection keys must contain exactly 32 bytes");
            }
            validated.put(version, new SecretKeySpec(material, "AES"));
        });
        if (!validated.containsKey(activeKeyVersion)) {
            throw new IllegalArgumentException(
                    "Active notification key version is absent from the key ring");
        }
        this.activeKeyVersion = activeKeyVersion;
        this.keys = Map.copyOf(validated);
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public ProtectedTenantProvisioningNotification protect(
            TenantProvisioningNotification notification) {
        Objects.requireNonNull(notification, "notification");
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keys.get(activeKeyVersion),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(activeKeyVersion));
            byte[] encrypted = cipher.doFinal(encode(notification));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return new ProtectedTenantProvisioningNotification(activeKeyVersion, payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot protect provisioning notification", exception);
        }
    }

    @Override
    public TenantProvisioningNotification unprotect(
            ProtectedTenantProvisioningNotification protectedNotification) {
        Objects.requireNonNull(protectedNotification, "protectedNotification");
        SecretKeySpec key = keys.get(protectedNotification.keyVersion());
        if (key == null) {
            throw new IllegalStateException("Notification protection key version is unavailable");
        }
        byte[] payload = protectedNotification.payload();
        if (payload.length <= NONCE_BYTES + 16) {
            throw new IllegalStateException("Protected notification payload is truncated");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        byte[] encrypted = new byte[payload.length - NONCE_BYTES];
        System.arraycopy(payload, 0, nonce, 0, nonce.length);
        System.arraycopy(payload, nonce.length, encrypted, 0, encrypted.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(protectedNotification.keyVersion()));
            return decode(cipher.doFinal(encrypted));
        } catch (AEADBadTagException exception) {
            throw new IllegalStateException(
                    "Protected notification authentication failed", exception);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot unprotect provisioning notification", exception);
        }
    }

    private byte[] encode(TenantProvisioningNotification notification) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(FORMAT_VERSION);
                writeString(output, notification.type().name());
                writeUuid(output, notification.provisioningRequestId());
                writeUuid(output, notification.tenantId());
                writeUuid(output, notification.subjectId());
                writeString(output, notification.deliveryChannel());
                writeString(output, notification.recipientReference());
                output.writeBoolean(notification.activationGrantId() != null);
                if (notification.activationGrantId() != null) {
                    writeUuid(output, notification.activationGrantId());
                    writeString(output, notification.activationSecret());
                }
                output.writeLong(notification.expiresAt().getEpochSecond());
                output.writeInt(notification.expiresAt().getNano());
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode provisioning notification", exception);
        }
    }

    private TenantProvisioningNotification decode(byte[] encoded) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int formatVersion = input.readInt();
            if (formatVersion != FORMAT_VERSION) {
                throw new IllegalStateException("Unsupported notification payload format");
            }
            TenantProvisioningNotificationType type =
                    TenantProvisioningNotificationType.valueOf(readString(input));
            UUID provisioningRequestId = readUuid(input);
            UUID tenantId = readUuid(input);
            UUID subjectId = readUuid(input);
            String deliveryChannel = readString(input);
            String recipientReference = readString(input);
            UUID activationGrantId = null;
            String activationSecret = null;
            if (input.readBoolean()) {
                activationGrantId = readUuid(input);
                activationSecret = readString(input);
            }
            Instant expiresAt = Instant.ofEpochSecond(input.readLong(), input.readInt());
            if (input.available() != 0) {
                throw new IllegalStateException("Notification payload contains trailing data");
            }
            return new TenantProvisioningNotification(
                    type,
                    provisioningRequestId,
                    tenantId,
                    subjectId,
                    deliveryChannel,
                    recipientReference,
                    activationGrantId,
                    activationSecret,
                    expiresAt);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot decode provisioning notification", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Notification string field is too large");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalStateException("Notification string field size is invalid");
        }
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) {
            throw new IllegalStateException("Notification string field is truncated");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static byte[] aad(String keyVersion) {
        return ("ainer:tenant-provisioning-notification:v1:" + keyVersion)
                .getBytes(StandardCharsets.UTF_8);
    }
}
