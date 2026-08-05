package dev.ainer.module.identity.foundation;

/**
 * Type-safe scan anchor for the Greenfield Identity foundation package. Referenced by
 * {@code IdentityModuleConfiguration}'s {@code @ComponentScan} / {@code @MapperScan} so the foundation
 * domain + persistence are wired alongside the legacy {@code account} package during the S1.2 coexistence
 * phase. Once the cutover removes the legacy package, this remains as the foundation scan anchor.
 */
public final class IdentityFoundationMarker {
    private IdentityFoundationMarker() {
    }
}
