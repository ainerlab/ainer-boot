package dev.ainer.security;

/** Stable security capabilities shared by framework and executable applications. */
public final class AinerSecurityScopes {

    public static final String PLATFORM_METRICS_READ = "platform.metrics.read";
    public static final String TENANT_MEMBERS_READ = "tenant.members.read";
    public static final String TENANT_MEMBERS_WRITE = "tenant.members.write";

    private AinerSecurityScopes() {
    }
}
