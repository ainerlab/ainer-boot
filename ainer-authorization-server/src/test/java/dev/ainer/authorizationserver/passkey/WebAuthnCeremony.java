package dev.ainer.authorizationserver.passkey;

import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.PublicKeyCredentialDescriptor;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.test.authenticator.webauthn.GetAssertionRequest;
import com.webauthn4j.test.authenticator.webauthn.GetAssertionResponse;
import com.webauthn4j.test.authenticator.webauthn.MakeCredentialRequest;
import com.webauthn4j.test.authenticator.webauthn.MakeCredentialResponse;
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 驱动真实 WebAuthn registration/authentication ceremony 的测试助手。
 *
 * <p>用 webauthn4j-test 的 {@link NoneAttestationAuthenticator} 产出与 Ainer
 * {@code attestation=none} 配置一致的 attestation/assertion，并组装成 Spring Security
 * WebAuthn 过滤器期望的标准 PublicKeyCredential JSON。它让 ceremony 校验代码路径在
 * 自动化测试里真实执行，而不是用合成 CredentialRecord 绕过签名验证。
 *
 * <p>这不替代真实设备/浏览器兼容矩阵：同一个 authenticator 实例必须先 {@link #register}
 * 再 {@link #authenticate}，以便私钥在内部 credential store 中可被 lookup。
 */
public final class WebAuthnCeremony {

    private static final Base64.Encoder B64U = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64U_DEC = Base64.getUrlDecoder();
    private static final PublicKeyCredentialType PUBLIC_KEY = PublicKeyCredentialType.create("public-key");
    private static final String ORIGIN_LOCALHOST = "http://localhost";

    private final NoneAttestationAuthenticator authenticator = new NoneAttestationAuthenticator();
    private final ObjectConverter objectConverter = new ObjectConverter();
    private final ObjectMapper objectMapper;

    public WebAuthnCeremony(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 根据 {@code /webauthn/register/options} 返回的 PublicKeyCredentialCreationOptions JSON
     * 产出注册请求体。
     *
     * @param creationOptionsJson 服务端 options 响应正文
     * @return 注册请求体与新生成 credential id
     */
    public Registration register(String creationOptionsJson) throws Exception {
        JsonNode options = objectMapper.readTree(creationOptionsJson);
        byte[] challenge = B64U_DEC.decode(options.path("challenge").stringValue());
        JsonNode rp = options.path("rp");
        JsonNode user = options.path("user");
        String clientDataJson = clientDataJson(challenge, "webauthn.create");

        MakeCredentialRequest request = new MakeCredentialRequest(
                sha256(clientDataJson),
                new PublicKeyCredentialRpEntity(rp.path("id").stringValue(), rp.path("name").stringValue()),
                new PublicKeyCredentialUserEntity(
                        B64U_DEC.decode(user.path("id").stringValue()),
                        user.path("name").stringValue(),
                        user.path("displayName").stringValue()),
                true,
                true,
                true,
                List.of(new PublicKeyCredentialParameters(PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)));
        MakeCredentialResponse response = authenticator.makeCredential(request);
        AttestationObject attestationObject = response.getAttestationObject();
        AttestedCredentialData attested = attestationObject.getAuthenticatorData().getAttestedCredentialData();
        byte[] credentialId = attested.getCredentialId();
        byte[] attestationBytes = objectConverter.getCborConverter().writeValueAsBytes(attestationObject);
        byte[] clientDataBytes = clientDataJson(challenge, "webauthn.create").getBytes(StandardCharsets.UTF_8);

        String credentialIdB64u = b64u(credentialId);
        Map<String, Object> attestationResponse = new LinkedHashMap<>();
        attestationResponse.put("attestationObject", b64u(attestationBytes));
        attestationResponse.put("clientDataJSON", b64u(clientDataBytes));
        attestationResponse.put("transports", List.of("internal"));
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", credentialIdB64u);
        credential.put("rawId", credentialIdB64u);
        credential.put("type", "public-key");
        credential.put("response", attestationResponse);
        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("credential", credential);
        publicKey.put("label", "test-passkey");
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("publicKey", publicKey);
        return new Registration(objectMapper.writeValueAsString(wrapper), credentialId);
    }

    /**
     * 根据 {@code /webauthn/authenticate/options} 返回的 PublicKeyCredentialRequestOptions JSON
     * 产出断言请求体。
     *
     * @param requestOptionsJson 服务端 options 响应正文
     * @param credentialId 来自同一 authenticator 的 {@link Registration#credentialId()}
     */
    public String authenticate(String requestOptionsJson, byte[] credentialId) throws Exception {
        JsonNode options = objectMapper.readTree(requestOptionsJson);
        byte[] challenge = B64U_DEC.decode(options.path("challenge").stringValue());
        String rpId = options.path("rpId").stringValue();
        String clientDataJson = clientDataJson(challenge, "webauthn.get");

        GetAssertionRequest request = new GetAssertionRequest(
                rpId,
                sha256(clientDataJson),
                List.of(new PublicKeyCredentialDescriptor(PUBLIC_KEY, credentialId, Set.of())),
                true,
                true,
                null);
        GetAssertionResponse assertion = authenticator.getAssertion(request);
        byte[] clientDataBytes = clientDataJson(challenge, "webauthn.get").getBytes(StandardCharsets.UTF_8);

        String credentialIdB64u = b64u(assertion.getCredentialId());
        Map<String, Object> assertionResponse = new LinkedHashMap<>();
        assertionResponse.put("authenticatorData", b64u(assertion.getAuthenticatorData()));
        assertionResponse.put("clientDataJSON", b64u(clientDataBytes));
        assertionResponse.put("signature", b64u(assertion.getSignature()));
        assertionResponse.put("userHandle", b64u(assertion.getUserHandle()));
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", credentialIdB64u);
        credential.put("rawId", credentialIdB64u);
        credential.put("type", "public-key");
        credential.put("response", assertionResponse);
        return objectMapper.writeValueAsString(credential);
    }

    private static String clientDataJson(byte[] challenge, String type) {
        return "{\"type\":\"%s\",\"challenge\":\"%s\",\"origin\":\"%s\"}".formatted(
                type, b64u(challenge), ORIGIN_LOCALHOST);
    }

    private static byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64u(byte[] bytes) {
        return B64U.encodeToString(bytes);
    }

    public record Registration(String payload, byte[] credentialId) {
    }
}
