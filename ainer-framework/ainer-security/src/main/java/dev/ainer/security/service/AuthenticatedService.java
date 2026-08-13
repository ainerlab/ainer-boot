package dev.ainer.security.service;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record AuthenticatedService(String serviceId, Set<String> authorities) {

    public static final String ACTOR_TYPE_CLAIM = "actor_type";
    public static final String SERVICE_ACTOR_TYPE = "SERVICE";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:@/-]{1,128}");

    public AuthenticatedService {
        if (serviceId == null || !IDENTIFIER.matcher(serviceId).matches()) {
            throw new IllegalArgumentException("Invalid service identifier");
        }
        authorities = Set.copyOf(Objects.requireNonNull(authorities, "authorities"));
    }

    public boolean hasAuthority(String authority) {
        return authorities.contains(authority);
    }

    public void requireAuthority(String authority) {
        if (!hasAuthority(authority)) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
    }

}
