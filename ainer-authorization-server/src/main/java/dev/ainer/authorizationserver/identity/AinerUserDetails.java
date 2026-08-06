package dev.ainer.authorizationserver.identity;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AinerUserDetails implements UserDetails, CredentialsContainer {

    private final UUID subjectId;
    private final UUID tenantId;
    private final UUID accountId;
    private final long securityEpoch;
    private final String username;
    private String password;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final List<GrantedAuthority> authorities;

    public AinerUserDetails(
            UUID subjectId,
            UUID tenantId,
            String username,
            String password,
            boolean enabled,
            boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities) {
        this(requireLegacyIdentity(subjectId, "subjectId"),
                requireLegacyIdentity(tenantId, "tenantId"),
                null, 0L, username, password, enabled, accountNonLocked, authorities);
    }

    public AinerUserDetails(
            UUID subjectId,
            UUID tenantId,
            UUID accountId,
            long securityEpoch,
            String username,
            String password,
            boolean enabled,
            boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities) {
        this.subjectId = subjectId;
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.securityEpoch = securityEpoch;
        this.username = Objects.requireNonNull(username, "username");
        this.password = password;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.authorities = List.copyOf(authorities);
    }

    public AinerUserDetails(
            UUID accountId,
            long securityEpoch,
            String username,
            String password,
            boolean enabled,
            boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities) {
        this(null, null, accountId, securityEpoch, username, password,
                enabled, accountNonLocked, authorities);
    }

    public UUID subjectId() {
        return subjectId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID accountId() {
        return accountId;
    }

    public long securityEpoch() {
        return securityEpoch;
    }

    public boolean hasLegacyTenantContext() {
        return subjectId != null && tenantId != null;
    }

    private static UUID requireLegacyIdentity(UUID value, String name) {
        return Objects.requireNonNull(value, name);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return new ArrayList<>(authorities);
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
