package dev.ainer.authorizationserver.passkey;

import dev.ainer.authorizationserver.config.AinerAuthorizationServerProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record AinerPasskeySettings(
        String rpId,
        String rpName,
        Set<String> allowedOrigins,
        Duration ceremonyTimeout) {

    private static final Pattern RP_ID_PATTERN = Pattern.compile(
            "^(?:localhost|(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*"
                    + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)$");
    private static final Pattern NUMERIC_HOST_PATTERN = Pattern.compile("^[0-9.]+$");
    private static final Duration MAX_CEREMONY_TIMEOUT = Duration.ofMinutes(10);

    public AinerPasskeySettings {
        if (rpId == null
                || !RP_ID_PATTERN.matcher(rpId).matches()
                || NUMERIC_HOST_PATTERN.matcher(rpId).matches()) {
            throw new IllegalStateException(
                    "Ainer passkey rp-id must be a lowercase DNS name or localhost");
        }
        if (rpName == null || rpName.isBlank() || rpName.length() > 100) {
            throw new IllegalStateException(
                    "Ainer passkey rp-name must contain 1..100 characters");
        }
        allowedOrigins = Set.copyOf(allowedOrigins);
        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException("Ainer passkey allowed-origins must not be empty");
        }
        if (ceremonyTimeout == null
                || ceremonyTimeout.isZero()
                || ceremonyTimeout.isNegative()
                || ceremonyTimeout.compareTo(MAX_CEREMONY_TIMEOUT) > 0) {
            throw new IllegalStateException(
                    "Ainer passkey ceremony-timeout must be greater than zero and at most 10 minutes");
        }
    }

    public static AinerPasskeySettings from(AinerAuthorizationServerProperties properties) {
        AinerAuthorizationServerProperties.Passkey passkey = properties.getPasskey();
        if (!passkey.isEnabled()) {
            throw new IllegalStateException("Ainer passkey settings requested while passkey is disabled");
        }
        String rpId = normalizeRpId(passkey.getRpId());
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        for (String configuredOrigin : passkey.getAllowedOrigins()) {
            String origin = validateAndNormalizeOrigin(
                    configuredOrigin, rpId, passkey.isAllowInsecureHttp());
            if (!origins.add(origin)) {
                throw new IllegalStateException(
                        "Ainer passkey allowed-origins must not contain duplicates");
            }
        }
        return new AinerPasskeySettings(
                rpId,
                passkey.getRpName() == null ? null : passkey.getRpName().trim(),
                origins,
                passkey.getCeremonyTimeout());
    }

    private static String normalizeRpId(String configuredRpId) {
        if (configuredRpId == null) {
            return null;
        }
        String rpId = configuredRpId.trim();
        if (!rpId.equals(rpId.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Ainer passkey rp-id must be lowercase");
        }
        return rpId;
    }

    private static String validateAndNormalizeOrigin(
            String configuredOrigin,
            String rpId,
            boolean allowInsecureHttp) {
        if (configuredOrigin == null || configuredOrigin.isBlank()) {
            throw new IllegalStateException(
                    "Ainer passkey allowed-origins must contain absolute origins");
        }
        URI uri;
        try {
            uri = URI.create(configuredOrigin.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Ainer passkey allowed-origins contains an invalid URI", exception);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || host == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalStateException(
                    "Ainer passkey allowed-origins entries must contain only scheme, host and optional port");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!normalizedHost.equals(rpId) && !normalizedHost.endsWith("." + rpId)) {
            throw new IllegalStateException(
                    "Ainer passkey origin host must equal or be a subdomain of rp-id");
        }
        if (uri.getPort() == 0 || uri.getPort() > 65_535) {
            throw new IllegalStateException(
                    "Ainer passkey origin port must be between 1 and 65535");
        }
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean loopbackHttp = "http".equalsIgnoreCase(scheme)
                && "localhost".equals(normalizedHost);
        if (!https && !(allowInsecureHttp && loopbackHttp)) {
            throw new IllegalStateException(
                    "Ainer passkey origins must use HTTPS; explicit insecure HTTP is limited to localhost tests");
        }
        try {
            return new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    null,
                    normalizedHost,
                    uri.getPort(),
                    null,
                    null,
                    null).toString();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Ainer passkey allowed-origins contains an invalid origin", exception);
        }
    }
}
