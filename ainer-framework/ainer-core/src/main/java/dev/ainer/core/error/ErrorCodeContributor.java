package dev.ainer.core.error;

import java.util.Collection;

/**
 * Supplies a bounded set of module error codes to the application registry.
 */
@FunctionalInterface
public interface ErrorCodeContributor {

    Collection<? extends ErrorCode> errorCodes();
}
