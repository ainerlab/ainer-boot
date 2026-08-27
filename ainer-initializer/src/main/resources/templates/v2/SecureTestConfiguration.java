package {{package.name}}.support;

import com.nimbusds.jose.jwk.RSAKey;
import dev.ainer.testsupport.jwt.JwtTestSupport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration(proxyBeanMethods = false)
public class SecureTestConfiguration {

    public static final String ISSUER = "https://auth.generated.ainer.test";
    public static final String AUDIENCE = "{{project.artifactId}}-api";
    private static final RSAKey KEY = JwtTestSupport.generateRsaKey();

    public static String userToken(String subjectId, String scopes) {
        return JwtTestSupport.signUserJwt(KEY, ISSUER, AUDIENCE, subjectId, scopes);
    }

    @Bean
    @Primary
    JwtDecoder generatedTestJwtDecoder() {
        return JwtTestSupport.jwtDecoder(KEY, ISSUER, AUDIENCE);
    }
}
