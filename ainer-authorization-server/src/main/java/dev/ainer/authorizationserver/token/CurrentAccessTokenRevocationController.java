package dev.ainer.authorizationserver.token;

import dev.ainer.core.error.BusinessException;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.core.web.ApiResponse;
import dev.ainer.web.request.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/access-token-revocations")
public class CurrentAccessTokenRevocationController {

    private static final String USER_ACTOR_TYPE = "USER";

    private final CurrentAccessTokenRevocationService revocationService;

    public CurrentAccessTokenRevocationController(CurrentAccessTokenRevocationService revocationService) {
        this.revocationService = revocationService;
    }

    @PostMapping
    public ApiResponse<CurrentAccessTokenRevocationResponse> revoke(
            Authentication authentication,
            HttpServletRequest request) {
        Jwt jwt = userJwt(authentication);
        revocationService.revoke(jwt.getTokenValue());
        return ApiResponse.success(
                new CurrentAccessTokenRevocationResponse(true),
                RequestIds.currentOrCreate(request));
    }

    private static Jwt userJwt(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()
                || !(jwtAuthentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(StandardErrorCode.UNAUTHENTICATED);
        }
        if (!USER_ACTOR_TYPE.equals(jwt.getClaimAsString("actor_type"))) {
            throw new BusinessException(StandardErrorCode.FORBIDDEN);
        }
        return jwt;
    }

    public record CurrentAccessTokenRevocationResponse(boolean revoked) {
    }
}
