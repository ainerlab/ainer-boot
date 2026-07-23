package dev.ainer.authorizationserver.identity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AinerUserDetails implements UserDetails {

    private final UUID subjectId;
    private final UUID tenantId;
    private final String username;
    private final String password;
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
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.authorities = List.copyOf(authorities);
    }

    public UUID subjectId() {
        return subjectId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
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
