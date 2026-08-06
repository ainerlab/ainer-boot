package dev.ainer.security.token;

/**
 * Resolves the currently authenticated request into the typed Foundation principal.
 */
public interface AuthenticatedPrincipalResolver {

    AuthenticatedPrincipal requireCurrent();
}
