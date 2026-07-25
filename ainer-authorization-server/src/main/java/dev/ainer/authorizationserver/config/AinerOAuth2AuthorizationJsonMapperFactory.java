package dev.ainer.authorizationserver.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ainer.authorizationserver.identity.AinerUserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.module.SimpleModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

final class AinerOAuth2AuthorizationJsonMapperFactory {

    private AinerOAuth2AuthorizationJsonMapperFactory() {
    }

    static JsonMapper create() {
        BasicPolymorphicTypeValidator.Builder validator =
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType(AinerUserDetails.class);
        List<JacksonModule> modules = new ArrayList<>(SecurityJacksonModules.getModules(
                AinerOAuth2AuthorizationJsonMapperFactory.class.getClassLoader(),
                validator));
        // spring-security-webauthn 的 Jackson 模块未通过 ServiceLoader 自动发现；Passkey 用户走授权码流程后，授权记录需往返 WebAuthnAuthentication 主体，必须显式注册并把其类型加入多态白名单。
        WebauthnJacksonModule webauthnModule = new WebauthnJacksonModule();
        webauthnModule.configurePolymorphicTypeValidator(validator);
        modules.add(webauthnModule);
        modules.add(new SimpleModule("ainer-oauth2-authorization")
                .setMixInAnnotation(AinerUserDetails.class, AinerUserDetailsMixin.class));
        return JsonMapper.builder().addModules(modules).build();
    }

    abstract static class AinerUserDetailsMixin {

        @JsonCreator
        AinerUserDetailsMixin(
                @JsonProperty("subjectId") UUID subjectId,
                @JsonProperty("tenantId") UUID tenantId,
                @JsonProperty("username") String username,
                @JsonProperty("password") String password,
                @JsonProperty("enabled") boolean enabled,
                @JsonProperty("accountNonLocked") boolean accountNonLocked,
                @JsonProperty("authorities")
                Collection<? extends GrantedAuthority> authorities) {
        }

        @JsonProperty("subjectId")
        abstract UUID subjectId();

        @JsonProperty("tenantId")
        abstract UUID tenantId();

        @JsonIgnore
        abstract String getPassword();
    }
}
