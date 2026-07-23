package dev.ainer.authorizationserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("ainer.security.authorization-server")
public class AinerAuthorizationServerProperties {

    private String issuer;
    private String audience = "ainer-api";
    private final SigningKey signingKey = new SigningKey();
    private final MachineClientBootstrap machineClientBootstrap = new MachineClientBootstrap();
    private final IntrospectionClientBootstrap introspectionClientBootstrap =
            new IntrospectionClientBootstrap();
    private final MetricsClientBootstrap metricsClientBootstrap = new MetricsClientBootstrap();
    private final ClientControlOperatorBootstrap clientControlOperatorBootstrap =
            new ClientControlOperatorBootstrap();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public SigningKey getSigningKey() {
        return signingKey;
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

    public ClientControlOperatorBootstrap getClientControlOperatorBootstrap() {
        return clientControlOperatorBootstrap;
    }

    public static final class SigningKey {

        private String keyId;
        private String privateKeyLocation;
        private String publicKeyLocation;

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getPrivateKeyLocation() {
            return privateKeyLocation;
        }

        public void setPrivateKeyLocation(String privateKeyLocation) {
            this.privateKeyLocation = privateKeyLocation;
        }

        public String getPublicKeyLocation() {
            return publicKeyLocation;
        }

        public void setPublicKeyLocation(String publicKeyLocation) {
            this.publicKeyLocation = publicKeyLocation;
        }
    }

    public static final class MachineClientBootstrap {

        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String tenantId;
        private List<String> scopes = new ArrayList<>(List.of("ai.invoke"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = new ArrayList<>(scopes);
        }
    }

    public static final class IntrospectionClientBootstrap {

        private boolean enabled;
        private String clientId;
        private String clientSecret;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }

    public static final class MetricsClientBootstrap {

        private boolean enabled;
        private String clientId;
        private String clientSecret;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }

    public static final class ClientControlOperatorBootstrap {

        private boolean enabled;
        private String clientId;
        private String clientSecret;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}
