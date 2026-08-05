package dev.ainer.authorizationserver.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.admin.browser-client")
public final class AinerAdminBrowserClientProperties {

    private final boolean enabled;
    private final String redirectUri;
    private final String postLogoutRedirectUri;

    public AinerAdminBrowserClientProperties(boolean enabled, String redirectUri, String postLogoutRedirectUri) {
        this.enabled = enabled;
        this.redirectUri = redirectUri;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getPostLogoutRedirectUri() {
        return postLogoutRedirectUri;
    }
}
