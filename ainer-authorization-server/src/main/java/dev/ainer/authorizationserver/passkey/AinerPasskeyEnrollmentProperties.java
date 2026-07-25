package dev.ainer.authorizationserver.passkey;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ainer.security.authorization-server.passkey.enrollment")
public final class AinerPasskeyEnrollmentProperties {

    private Mode mode = Mode.OPTIONAL;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OPTIONAL : mode;
    }

    public boolean isRequireInvite() {
        return mode == Mode.REQUIRE_INVITE;
    }

    public enum Mode {
        OPTIONAL,
        REQUIRE_INVITE
    }
}
