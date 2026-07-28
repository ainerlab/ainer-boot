package dev.ainer.security;

/** Stable security capabilities shared by framework and executable applications. */
public final class AinerSecurityScopes {

    public static final String PLATFORM_METRICS_READ = "platform.metrics.read";
    public static final String PLATFORM_TENANTS_READ = "platform.tenants.read";
    public static final String PLATFORM_TENANTS_WRITE = "platform.tenants.write";
    public static final String PLATFORM_USERS_READ = "platform.users.read";
    public static final String PLATFORM_USERS_WRITE = "platform.users.write";
    public static final String IDENTITY_PROVISIONING_ACCEPT = "identity.provisioning.accept";
    public static final String IDENTITY_PROVISIONING_NOTIFICATIONS_PUBLISH =
            "identity.provisioning-notifications.publish";
    public static final String IDENTITY_PROVISIONING_NOTIFICATION_RECEIPTS_WRITE =
            "identity.provisioning-notifications.receipts.write";
    public static final String TENANT_MEMBERS_READ = "tenant.members.read";
    public static final String TENANT_MEMBERS_WRITE = "tenant.members.write";
    public static final String TENANT_OWNERSHIP_TRANSFER = "tenant.ownership.transfer";

    private AinerSecurityScopes() {
    }
}
