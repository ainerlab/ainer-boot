package dev.ainer.authorizationserver.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.admin.browser-client")
public final class AinerAdminBrowserClientProperties {

    private boolean enabled;
    private String redirectUri;
    private String postLogoutRedirectUri;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getPostLogoutRedirectUri() {
        return postLogoutRedirectUri;
    }

    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }
}
