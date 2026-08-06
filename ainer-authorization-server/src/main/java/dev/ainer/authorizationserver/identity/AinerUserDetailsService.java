package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityAccount;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import dev.ainer.module.identity.foundation.IdentityFoundationService;
import dev.ainer.module.identity.foundation.LoginIdentityType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Locale;

public final class AinerUserDetailsService implements UserDetailsService {

    private final IdentityFoundationService foundationService;
    private final IdentityApplicationService identityService;
    private final String providerAuthority;

    public AinerUserDetailsService(IdentityApplicationService identityService) {
        this(null, identityService, null);
    }

    public AinerUserDetailsService(
            IdentityFoundationService foundationService,
            IdentityApplicationService identityService,
            String providerAuthority) {
        this.foundationService = foundationService;
        this.identityService = identityService;
        this.providerAuthority = providerAuthority;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = normalize(username);
        if (foundationService != null && providerAuthority != null) {
            IdentityFoundationService.CredentialLookup credential = foundationService
                    .findPasswordCredentialForLogin(
                            LoginIdentityType.USERNAME,
                            providerAuthority,
                            normalizedUsername)
                    .orElse(null);
            if (credential != null) {
                IdentityAccount legacyContext = identityService == null
                        ? null
                        : identityService.findAccountByUsername(normalizedUsername).orElse(null);
                if (legacyContext != null) {
                    return new AinerUserDetails(
                            legacyContext.subjectId(),
                            legacyContext.tenantId(),
                            credential.account().accountId(),
                            credential.account().securityEpoch(),
                            normalizedUsername,
                            credential.credential().credentialData(),
                            true,
                            true,
                            legacyContext.roles().stream()
                                    .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                    .toList());
                }
                return new AinerUserDetails(
                        credential.account().accountId(),
                        credential.account().securityEpoch(),
                        normalizedUsername,
                        credential.credential().credentialData(),
                        true,
                        true,
                        List.of());
            }
        }
        if (identityService == null) {
            throw new UsernameNotFoundException("Identity account not found");
        }
        IdentityAccount account = identityService.findAccountByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Identity account not found"));
        return new AinerUserDetails(
                account.subjectId(),
                account.tenantId(),
                account.username(),
                account.passwordHash(),
                account.enabled(),
                account.accountNonLocked(),
                account.roles().stream()
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .toList());
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Identity account not found");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
