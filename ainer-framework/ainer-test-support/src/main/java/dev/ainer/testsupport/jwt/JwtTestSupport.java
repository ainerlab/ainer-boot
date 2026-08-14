package dev.ainer.testsupport.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.ainer.security.token.AuthenticatedPrincipal;
import dev.ainer.security.token.AuthenticatedPrincipalResolver;
import dev.ainer.security.token.ReferenceTokenProfileResolver;
import dev.ainer.security.token.VerifiedJwtClaims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.LinkedHashSet;

/**
 * Real-JWT test fixtures for {@code @SpringBootTest(RANDOM_PORT)} module HTTP tests.
 *
 * <p>Signs USER_NEUTRAL_V1 / SERVICE_V1 tokens matching the Greenfield claim contract
 * ({@code token_profile}, {@code claim_contract_version}, {@code actor_type}, {@code scope},
 * {@code amr}, {@code sec_epoch}) with a class-scoped RSA key, and supplies a real
 * {@link JwtDecoder} plus the production-equivalent {@code AuthenticatedPrincipalResolver} used
 * as {@code @Primary} in test configurations.
 */
public final class JwtTestSupport {

    private JwtTestSupport() {
    }

    /** Generates a fresh 3072-bit RSA key pair for one test class (no external PEM dependency). */
    public static RSAKey generateRsaKey() {
        try {
            return new RSAKeyGenerator(3072).keyID("test-kid").generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate test RSA key", exception);
        }
    }

    /** Signs a USER_NEUTRAL_V1 JWT carrying the given space-separated scopes. */
    public static String signUserJwt(RSAKey jwk, String issuer, String audience,
            String subjectId, String scopes) {
        return signJwt(jwk, issuer, audience, subjectId, scopes, "USER_NEUTRAL_V1", "USER", "pwd");
    }

    /** Signs a SERVICE_V1 JWT carrying the given space-separated scopes. */
    public static String signServiceJwt(RSAKey jwk, String issuer, String audience,
            String subjectId, String scopes) {
        return signJwt(jwk, issuer, audience, subjectId, scopes, "SERVICE_V1", "SERVICE",
                "client_credentials");
    }

    /** Signs a JWT matching the claim contract expected by {@link ReferenceTokenProfileResolver}. */
    public static String signJwt(RSAKey jwk, String issuer, String audience, String subjectId,
            String scopes, String tokenProfile, String actorType, String assurance) {
        try {
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(),
                    new JWTClaimsSet.Builder()
                            .issuer(issuer)
                            .audience(audience)
                            .subject(subjectId)
                            .claim("token_profile", tokenProfile)
                            .claim("claim_contract_version", "1")
                            .claim("actor_type", actorType)
                            .claim("scope", scopes)
                            .claim("amr", assurance)
                            .claim("client_id", "test-client")
                            .claim("sec_epoch", 0L)
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                            .build());
            signedJWT.sign(new RSASSASigner(jwk.toRSAPrivateKey()));
            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign test JWT", exception);
        }
    }

    /** Real decoder verifying signatures with the test public key plus issuer/audience checks. */
    public static JwtDecoder jwtDecoder(RSAKey jwk, String issuer, String audience) {
        try {
            RSAPublicKey publicKey = jwk.toRSAPublicKey();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
            OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                    jwt.getAudience().contains(audience)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                    "invalid_token", "Required audience is missing", null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
            return decoder;
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("Failed to derive test public key", exception);
        }
    }

    /**
     * Production-equivalent resolver reading the verified {@code Jwt} from the SecurityContext and
     * resolving it through {@link ReferenceTokenProfileResolver}. Register as {@code @Primary} in
     * test configurations so it wins over any resolver leaking from other tests' scan scope.
     */
    public static AuthenticatedPrincipalResolver principalResolver() {
        ReferenceTokenProfileResolver profileResolver = new ReferenceTokenProfileResolver();
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                throw new dev.ainer.core.error.BusinessException(
                        dev.ainer.core.error.StandardErrorCode.UNAUTHENTICATED);
            }
            if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
                throw new dev.ainer.core.error.BusinessException(
                        dev.ainer.core.error.StandardErrorCode.FORBIDDEN);
            }
            return profileResolver.resolve(new VerifiedJwtClaims(
                    jwt.getIssuer().toString(),
                    jwt.getSubject(),
                    new LinkedHashSet<>(jwt.getAudience()),
                    jwt.getExpiresAt(),
                    jwt.getClaims()));
        };
    }
}
