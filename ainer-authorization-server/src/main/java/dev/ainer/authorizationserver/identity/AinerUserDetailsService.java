package dev.ainer.authorizationserver.identity;

import dev.ainer.module.identity.account.application.IdentityAccount;
import dev.ainer.module.identity.account.application.IdentityApplicationService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public final class AinerUserDetailsService implements UserDetailsService {

    private final IdentityApplicationService identityService;

    public AinerUserDetailsService(IdentityApplicationService identityService) {
        this.identityService = identityService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        IdentityAccount account = identityService.findAccountByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Identity account not found"));
        return new AinerUserDetails(
                account.subjectId(),
                account.tenantId(),
                account.username(),
                account.passwordHash(),
                account.enabled(),
                account.accountNonLocked(),
                account.roles().stream().map(SimpleGrantedAuthority::new).toList());
    }
}
