package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentityType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Locale;
import java.util.Objects;

public final class AinerUserDetailsService implements UserDetailsService {

    private final IdentityFoundationService foundationService;
    private final String providerAuthority;

    public AinerUserDetailsService(
            IdentityFoundationService foundationService,
            String providerAuthority) {
        this.foundationService = Objects.requireNonNull(foundationService, "foundationService");
        this.providerAuthority = Objects.requireNonNull(providerAuthority, "providerAuthority");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = normalize(username);
        IdentityFoundationService.CredentialLookup credential = foundationService
                .findPasswordCredentialForLogin(
                        LoginIdentityType.USERNAME,
                        providerAuthority,
                        normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Identity account not found"));
        return new AinerUserDetails(
                credential.account().accountId(),
                credential.account().securityEpoch(),
                normalizedUsername,
                credential.credential().credentialData(),
                credential.account().status().canAuthenticate(),
                credential.account().status().canAuthenticate(),
                java.util.List.of());
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Identity account not found");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
