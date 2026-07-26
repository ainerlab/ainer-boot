package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.TenantProvisioningNotificationPayloadProtector;
import dev.ainer.module.identity.account.application.TenantProvisioningPolicy;
import dev.ainer.module.identity.account.infrastructure.security.AesGcmTenantProvisioningNotificationPayloadProtector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformIdentityControlProperties.class)
@ConditionalOnProperty(
        prefix = "ainer.identity.platform-control",
        name = "enabled",
        havingValue = "true")
public class PlatformIdentityControlConfiguration {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");
    private static final Pattern KEY_VERSION = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    @Bean
    PlatformIdentityControlSettings platformIdentityControlSettings(
            PlatformIdentityControlProperties properties) {
        LinkedHashSet<String> operators = new LinkedHashSet<>();
        for (String value : properties.getOperatorClientIds()) {
            if (value == null || !IDENTIFIER.matcher(value).matches()) {
                throw new IllegalStateException(
                        "Ainer platform identity operator client id is invalid");
            }
            operators.add(value);
        }
        if (operators.isEmpty()) {
            throw new IllegalStateException(
                    "Ainer platform identity operator-client-ids must not be empty");
        }
        TenantProvisioningPolicy policy;
        try {
            policy = new TenantProvisioningPolicy(
                    properties.getRequestTtl(),
                    properties.getActivationTtl(),
                    properties.getActivationMaxAttempts());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException(
                    "Ainer platform identity request-ttl, activation-ttl, "
                            + "or activation-max-attempts is invalid",
                    exception);
        }
        String activeKeyVersion =
                properties.getNotificationProtectionActiveKeyVersion().trim();
        if (!KEY_VERSION.matcher(activeKeyVersion).matches()) {
            throw new IllegalStateException(
                    "Ainer platform identity notification active key version is invalid");
        }
        Map<String, byte[]> keyRing = parseKeyRing(
                properties.getNotificationProtectionKeys());
        if (!keyRing.containsKey(activeKeyVersion)) {
            throw new IllegalStateException(
                    "Ainer platform identity notification active key is missing");
        }
        return new PlatformIdentityControlSettings(
                Set.copyOf(operators),
                policy,
                activeKeyVersion,
                keyRing);
    }

    @Bean
    TenantProvisioningNotificationPayloadProtector
            tenantProvisioningNotificationPayloadProtector(
                    PlatformIdentityControlSettings settings) {
        return new AesGcmTenantProvisioningNotificationPayloadProtector(
                settings.notificationProtectionActiveKeyVersion(),
                settings.notificationProtectionKeys(),
                new SecureRandom());
    }

    private Map<String, byte[]> parseKeyRing(List<String> configuredKeys) {
        LinkedHashMap<String, byte[]> keyRing = new LinkedHashMap<>();
        for (String configured : configuredKeys) {
            if (configured == null) {
                throw new IllegalStateException(
                        "Ainer platform identity notification key is invalid");
            }
            int separator = configured.indexOf(':');
            if (separator < 1 || separator == configured.length() - 1) {
                throw new IllegalStateException(
                        "Ainer platform identity notification key must use version:base64url");
            }
            String version = configured.substring(0, separator).trim();
            if (!KEY_VERSION.matcher(version).matches()) {
                throw new IllegalStateException(
                        "Ainer platform identity notification key version is invalid");
            }
            byte[] key;
            try {
                key = Base64.getUrlDecoder().decode(
                        configured.substring(separator + 1).trim());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Ainer platform identity notification key is not valid base64url",
                        exception);
            }
            if (key.length != 32 || keyRing.putIfAbsent(version, key) != null) {
                throw new IllegalStateException(
                        "Ainer platform identity notification key ring is invalid");
            }
        }
        if (keyRing.isEmpty()) {
            throw new IllegalStateException(
                    "Ainer platform identity notification key ring must not be empty");
        }
        return Map.copyOf(keyRing);
    }
}
