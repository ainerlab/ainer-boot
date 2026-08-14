package dev.ainer.module.file.file.application;

/**
 * Scope constants for the file module (ADR-0040). Checked imperatively via
 * {@code AuthenticatedPrincipal.hasScope(...)}; the resource-server filter chain only enforces
 * authentication, scopes are module responsibility.
 */
public final class FileAuthorities {

    /** Read metadata and download content. */
    public static final String READ = "file.read";

    /** Upload and delete. */
    public static final String WRITE = "file.write";

    private FileAuthorities() {
    }
}
