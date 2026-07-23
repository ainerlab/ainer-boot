package dev.ainer.authorizationserver.passkey;

import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagerFactories;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.WebAuthnConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import java.util.function.Predicate;

public final class AinerPasskeyWebSecurity {

    private final AinerPasskeySettings settings;
    private final AuthorizationManager<RequestAuthorizationContext> authorizationManager;

    public AinerPasskeyWebSecurity(
            AinerPasskeySettings settings,
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials) {
        this.settings = settings;
        Predicate<Authentication> registered =
                new RegisteredPasskeyPredicate(userEntities, userCredentials);
        DefaultAuthorizationManagerFactory<RequestAuthorizationContext> factory =
                AuthorizationManagerFactories.<RequestAuthorizationContext>multiFactor()
                        .requireFactors(FactorGrantedAuthority.WEBAUTHN_AUTHORITY)
                        .when(registered)
                        .build();
        this.authorizationManager = factory.authenticated();
    }

    public AuthorizationManager<RequestAuthorizationContext> authorizationManager() {
        return authorizationManager;
    }

    public void configureProtocolChain(WebAuthnConfigurer<HttpSecurity> webAuthn) {
        configure(webAuthn);
        webAuthn.disableDefaultRegistrationPage(true);
    }

    public void configureBrowserChain(WebAuthnConfigurer<HttpSecurity> webAuthn) {
        configure(webAuthn);
    }

    public void configureFormLogin(FormLoginConfigurer<HttpSecurity> formLogin) {
        formLogin.withObjectPostProcessor(
                new MfaFilterPostProcessor<UsernamePasswordAuthenticationFilter>());
    }

    private void configure(WebAuthnConfigurer<HttpSecurity> webAuthn) {
        webAuthn.rpId(settings.rpId())
                .rpName(settings.rpName())
                .allowedOrigins(settings.allowedOrigins())
                .withObjectPostProcessor(
                        new MfaFilterPostProcessor<WebAuthnAuthenticationFilter>());
    }

    private record RegisteredPasskeyPredicate(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials) implements Predicate<Authentication> {

        @Override
        public boolean test(Authentication authentication) {
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication.getName() == null) {
                return false;
            }
            PublicKeyCredentialUserEntity user =
                    userEntities.findByUsername(authentication.getName());
            return user != null && !userCredentials.findByUserId(user.getId()).isEmpty();
        }

        @Override
        public String toString() {
            return "AINER_ACTIVE_PASSKEY_REGISTERED";
        }
    }

    private static final class MfaFilterPostProcessor<T extends AbstractAuthenticationProcessingFilter>
            implements ObjectPostProcessor<T> {

        @Override
        public <O extends T> O postProcess(O filter) {
            filter.setMfaEnabled(true);
            return filter;
        }
    }
}
