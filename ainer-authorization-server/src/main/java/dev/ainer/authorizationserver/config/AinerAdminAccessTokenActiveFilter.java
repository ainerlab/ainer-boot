package dev.ainer.authorizationserver.config;

import dev.ainer.core.error.ErrorCode;
import dev.ainer.core.error.StandardErrorCode;
import dev.ainer.security.autoconfigure.AinerSecurityFailureWriter;
import dev.ainer.security.error.AinerSecurityErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

final class AinerAdminAccessTokenActiveFilter extends OncePerRequestFilter {

    private final BearerTokenResolver bearerTokenResolver;
    private final OAuth2AuthorizationService authorizationService;
    private final AinerSecurityFailureWriter failureWriter;

    AinerAdminAccessTokenActiveFilter(
            BearerTokenResolver bearerTokenResolver,
            OAuth2AuthorizationService authorizationService,
            AinerSecurityFailureWriter failureWriter) {
        this.bearerTokenResolver = bearerTokenResolver;
        this.authorizationService = authorizationService;
        this.failureWriter = failureWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            reject(request, response, StandardErrorCode.UNAUTHENTICATED);
            return;
        }

        try {
            String tokenValue = bearerTokenResolver.resolve(request);
            OAuth2Authorization authorization = tokenValue == null
                    ? null
                    : authorizationService.findByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN);
            if (!isActiveAccessToken(authorization, tokenValue)) {
                reject(request, response, StandardErrorCode.UNAUTHENTICATED);
                return;
            }
        } catch (OAuth2AuthenticationException exception) {
            reject(request, response, StandardErrorCode.UNAUTHENTICATED);
            return;
        } catch (RuntimeException exception) {
            reject(request, response, AinerSecurityErrorCode.ONLINE_VALIDATION_UNAVAILABLE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isActiveAccessToken(
            OAuth2Authorization authorization,
            String tokenValue) {
        return authorization != null
                && authorization.getAccessToken() != null
                && Objects.equals(
                        tokenValue,
                        authorization.getAccessToken().getToken().getTokenValue())
                && authorization.getAccessToken().isActive();
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode) throws IOException {
        SecurityContextHolder.clearContext();
        failureWriter.write(request, response, errorCode);
    }
}
