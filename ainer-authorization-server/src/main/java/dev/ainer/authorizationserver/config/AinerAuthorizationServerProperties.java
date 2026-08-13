package dev.ainer.authorizationserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("ainer.security.authorization-server")
public class AinerAuthorizationServerProperties {

    private final String issuer;
    private final String audience;
    private final SigningKey signingKey;
    private final Passkey passkey;
    private final MachineClientBootstrap machineClientBootstrap;
    private final IntrospectionClientBootstrap introspectionClientBootstrap;
    private final MetricsClientBootstrap metricsClientBootstrap;
    private final BrowserClientControlOperatorBootstrap browserClientControlOperatorBootstrap;

    public AinerAuthorizationServerProperties(
            String issuer,
            String audience,
            SigningKey signingKey,
            Passkey passkey,
            MachineClientBootstrap machineClientBootstrap,
            IntrospectionClientBootstrap introspectionClientBootstrap,
            MetricsClientBootstrap metricsClientBootstrap,
            BrowserClientControlOperatorBootstrap browserClientControlOperatorBootstrap) {
        this.issuer = issuer;
        this.audience = audience != null ? audience : "ainer-api";
        this.signingKey = signingKey != null ? signingKey : new SigningKey(null, null, null);
        this.passkey = passkey != null ? passkey : new Passkey(false, null, null, null, false, null);
        this.machineClientBootstrap = machineClientBootstrap != null
                ? machineClientBootstrap
                : new MachineClientBootstrap(false, null, null, null);
        this.introspectionClientBootstrap = introspectionClientBootstrap != null
                ? introspectionClientBootstrap
                : new IntrospectionClientBootstrap(false, null, null);
        this.metricsClientBootstrap = metricsClientBootstrap != null
                ? metricsClientBootstrap
                : new MetricsClientBootstrap(false, null, null);
        this.browserClientControlOperatorBootstrap = browserClientControlOperatorBootstrap != null
                ? browserClientControlOperatorBootstrap
                : new BrowserClientControlOperatorBootstrap(false, null, null);
    }

    public String getIssuer() {
        return issuer;
    }

    public String getAudience() {
        return audience;
    }

    public SigningKey getSigningKey() {
        return signingKey;
    }

    public Passkey getPasskey() {
        return passkey;
    }

    public MachineClientBootstrap getMachineClientBootstrap() {
        return machineClientBootstrap;
    }

    public IntrospectionClientBootstrap getIntrospectionClientBootstrap() {
        return introspectionClientBootstrap;
    }

    public MetricsClientBootstrap getMetricsClientBootstrap() {
        return metricsClientBootstrap;
    }

    public BrowserClientControlOperatorBootstrap getBrowserClientControlOperatorBootstrap() {
        return browserClientControlOperatorBootstrap;
    }

    public static final class SigningKey {

        private final String keyId;
        private final String privateKeyLocation;
        private final String publicKeyLocation;

        public SigningKey(String keyId, String privateKeyLocation, String publicKeyLocation) {
            this.keyId = keyId;
            this.privateKeyLocation = privateKeyLocation;
            this.publicKeyLocation = publicKeyLocation;
        }

        public String getKeyId() {
            return keyId;
        }

        public String getPrivateKeyLocation() {
            return privateKeyLocation;
        }

        public String getPublicKeyLocation() {
            return publicKeyLocation;
        }
    }

    public static final class Passkey {

        private final boolean enabled;
        private final String rpId;
        private final String rpName;
        private final List<String> allowedOrigins;
        private final boolean allowInsecureHttp;
        private final Duration ceremonyTimeout;

        public Passkey(
                boolean enabled,
                String rpId,
                String rpName,
                List<String> allowedOrigins,
                boolean allowInsecureHttp,
                Duration ceremonyTimeout) {
            this.enabled = enabled;
            this.rpId = rpId;
            this.rpName = rpName != null ? rpName : "Ainer";
            this.allowedOrigins = allowedOrigins != null ? new ArrayList<>(allowedOrigins) : new ArrayList<>();
            this.allowInsecureHttp = allowInsecureHttp;
            this.ceremonyTimeout = ceremonyTimeout != null ? ceremonyTimeout : Duration.ofMinutes(5);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getRpId() {
            return rpId;
        }

        public String getRpName() {
            return rpName;
        }

        public List<String> getAllowedOrigins() {
            return new ArrayList<>(allowedOrigins);
        }

        public boolean isAllowInsecureHttp() {
            return allowInsecureHttp;
        }

        public Duration getCeremonyTimeout() {
            return ceremonyTimeout;
        }
    }

    public static final class MachineClientBootstrap {

        private final boolean enabled;
        private final String clientId;
        private final String clientSecret;
        private final List<String> scopes;

        public MachineClientBootstrap(
                boolean enabled, String clientId, String clientSecret, List<String> scopes) {
            this.enabled = enabled;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.scopes = scopes != null ? new ArrayList<>(scopes) : new ArrayList<>(List.of("ai.invoke"));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public List<String> getScopes() {
            return List.copyOf(scopes);
        }
    }

    public static final class IntrospectionClientBootstrap {

        private final boolean enabled;
        private final String clientId;
        private final String clientSecret;

        public IntrospectionClientBootstrap(boolean enabled, String clientId, String clientSecret) {
            this.enabled = enabled;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }
    }

    public static final class MetricsClientBootstrap {

        private final boolean enabled;
        private final String clientId;
        private final String clientSecret;

        public MetricsClientBootstrap(boolean enabled, String clientId, String clientSecret) {
            this.enabled = enabled;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }
    }

    public static final class BrowserClientControlOperatorBootstrap {

        private final boolean enabled;
        private final String clientId;
        private final String clientSecret;

        public BrowserClientControlOperatorBootstrap(boolean enabled, String clientId, String clientSecret) {
            this.enabled = enabled;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }
    }

}
