package dev.ainer.core.error;

import java.util.Collection;

/**
 * 向应用级注册表提供一组有界错误码。
 */
@FunctionalInterface
public interface ErrorCodeContributor {

    Collection<? extends ErrorCode> errorCodes();
}
