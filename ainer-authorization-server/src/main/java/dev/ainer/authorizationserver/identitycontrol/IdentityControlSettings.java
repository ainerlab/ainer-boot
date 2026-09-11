package dev.ainer.authorizationserver.identitycontrol;

/**
 * 身份生命周期控制面的可信调用方设置。
 *
 * @param trustedServiceId 唯一受信 SERVICE 主体的 {@code sub}（ServicePrincipal UUID）。
 */
public record IdentityControlSettings(String trustedServiceId) {
}
