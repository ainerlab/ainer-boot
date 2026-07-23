package dev.ainer.security.actor;

/**
 * Resolves the current actor from a trusted authentication mechanism.
 */
@FunctionalInterface
public interface AuthenticatedActorResolver {

    AuthenticatedActor requireCurrent();
}
