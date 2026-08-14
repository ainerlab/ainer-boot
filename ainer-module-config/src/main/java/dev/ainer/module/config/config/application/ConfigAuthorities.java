package dev.ainer.module.config.config.application;

/**
 * Scope constants for the config module (ADR-0040). Checked imperatively via
 * {@code AuthenticatedPrincipal.hasScope(...)}; runtime reads (getValue/getTyped/getSecret) are
 * internal product paths and stay unscoped.
 */
public final class ConfigAuthorities {

    /** List entries and read change history. */
    public static final String READ = "config.read";

    /** Set values and secrets. */
    public static final String MANAGE = "config.manage";

    private ConfigAuthorities() {
    }
}
