package dev.ainer.authorization.spring;

import dev.ainer.authorization.domain.AccessMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the Ainer permission required for a handler method (ADR-0037 §4, ADR-0030 §8.3).
 *
 * <p>The annotation references a <strong>stable PermissionCode</strong> registered in the
 * {@link dev.ainer.authorization.catalog.PermissionRegistry}. It does not contain SpEL or arbitrary
 * policy — it is a declarative marker consumed by {@link AinerAuthorizeInterceptor}, which sets the
 * permission as a request attribute before the {@link AinerRequestAuthorizationManager} evaluates.
 *
 * <p>{@link #accessMode()} defaults to {@link AccessMode#AUTHENTICATED authenticated}. A method may
 * opt into {@link AccessMode#PUBLIC_PROJECTION public} only when it also serves anonymous access
 * via an explicit {@link dev.ainer.authorization.policy.PublicAccessPolicy}.
 *
 * <p>High-risk business writes must still call {@link dev.ainer.authorization.AuthorizationService}
 * explicitly in the application service (ADR-0030 §8.4) — this annotation is an HTTP-layer
 * coarse gate, not a replacement for fine-grained application-level authorization.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AinerAuthorize {

    /** The stable permission code required to access this method. */
    String permission();

    /** The access mode for this endpoint. Defaults to {@link AccessMode#AUTHENTICATED}. */
    AccessMode accessMode() default AccessMode.AUTHENTICATED;
}
