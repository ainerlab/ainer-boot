package dev.ainer.module.dictionary.dictionary.application;

/**
 * Scope constants for the dictionary module (ADR-0040). Checked imperatively via
 * {@code AuthenticatedPrincipal.hasScope(...)} in the application service.
 */
public final class DictionaryAuthorities {

    /** Read types/items and resolve cached projections. */
    public static final String READ = "dictionary.read";

    /** Create/update/disable types and items. */
    public static final String MANAGE = "dictionary.manage";

    private DictionaryAuthorities() {
    }
}
