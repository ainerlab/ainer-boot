package dev.ainer.authorizationserver.config;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBC authorization 的 JSON 往返必须能读回 access token 的 claim 元数据。
 *
 * <p>回归背景：`oauth2_authorization.access_token_metadata` 以 `Map&lt;String,Object&gt;` 保存 claims，
 * 在启用了默认多态类型的前提下，装箱标量会被写成类型 id（`sec_epoch` → `["java.lang.Long",0]`）。
 * 如果 {@code PolymorphicTypeValidator} 不放行这些 JDK 标量，`findByToken` 会在反序列化时抛
 * `InvalidTypeIdException`：introspection、刷新与撤销查找全部失败，Resource Server 的在线校验
 * 退化成 503，文档承诺的"epoch 不匹配即 inactive → 401"永远走不到。生产 Token 全部携带
 * `sec_epoch`，所以这不是测试专用路径。
 */
class AinerOAuth2AuthorizationJsonMapperFactoryTest {

    private static final TypeReference<Map<String, Object>> CLAIMS =
            new TypeReference<>() {};

    private final JsonMapper mapper = AinerOAuth2AuthorizationJsonMapperFactory.create();

    @Test
    void accessTokenClaimsWithBoxedScalarsSurvivePolymorphicRoundTrip() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "019c7100-0000-7000-8000-000000000001");
        claims.put("sec_epoch", 7L);
        claims.put("token_profile", "USER_NEUTRAL_V1");
        claims.put("claim_contract_version", "1");
        claims.put("epoch_boundary", 1);

        String json = mapper.writerFor(CLAIMS).writeValueAsString(claims);

        // 类型 id 确实被写入（这就是失败模式本身），因此必须由白名单放行而不是靠"恰好没写"
        assertThat(json).contains("java.lang.Long");
        assertThat(mapper.readValue(json, CLAIMS))
                .containsEntry("sec_epoch", 7L)
                .containsEntry("epoch_boundary", 1)
                .containsEntry("sub", "019c7100-0000-7000-8000-000000000001");
    }
}
