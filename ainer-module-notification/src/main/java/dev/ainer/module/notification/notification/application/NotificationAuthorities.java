package dev.ainer.module.notification.notification.application;

/**
 * Scope constants for the notification module (ADR-0040).
 */
public final class NotificationAuthorities {

    /** Page templates and delivery records. */
    public static final String READ = "notification.read";

    /** Create/update/disable templates. */
    public static final String MANAGE = "notification.manage";

    /** Submit notification intents for delivery. */
    public static final String SUBMIT = "notification.submit";

    private NotificationAuthorities() {
    }
}
