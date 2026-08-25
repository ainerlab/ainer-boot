package dev.ainer.module.notification.notification.application;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

/**
 * Webhook recipient 的失败关闭校验：绝对 URL、host 白名单、HTTPS（或显式允许的
 * loopback HTTP），并拒绝解析到私网 / 链路本地 / ULA / 组播的地址，降低 SSRF。
 * 异常消息不含完整 URL、query 或用户信息。
 */
public final class WebhookDestinationRules {

    private static final int MAX_RECIPIENT_LENGTH = 2048;

    private WebhookDestinationRules() {
    }

    public static URI validate(String recipient, NotificationWebhookProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (recipient == null || recipient.isBlank() || recipient.length() > MAX_RECIPIENT_LENGTH) {
            throw new IllegalArgumentException("Webhook destination is not allowed");
        }
        URI uri;
        try {
            uri = URI.create(recipient.strip());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Webhook destination is not allowed");
        }
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Webhook destination is not allowed");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!properties.allowedHosts().contains(host)) {
            throw new IllegalArgumentException("Webhook destination is not allowed");
        }
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean insecureLoopbackHttp = "http".equalsIgnoreCase(uri.getScheme())
                && properties.allowInsecureHttp()
                && isLoopbackHost(host);
        if (!https && !insecureLoopbackHttp) {
            throw new IllegalArgumentException("Webhook destination is not allowed");
        }
        rejectBlockedAddresses(host, properties.allowInsecureHttp());
        return uri;
    }

    private static void rejectBlockedAddresses(String host, boolean allowInsecureHttp) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Webhook destination is not allowed");
        }
        if (addresses.length == 0) {
            throw new IllegalArgumentException("Webhook destination is not allowed");
        }
        for (InetAddress address : addresses) {
            InetAddress target = unwrapIpv4Mapped(address);
            if (isBlockedAddress(target, allowInsecureHttp)) {
                throw new IllegalArgumentException("Webhook destination is not allowed");
            }
        }
    }

    static boolean isBlockedAddress(InetAddress address, boolean allowInsecureHttp) {
        if (address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address)) {
            return true;
        }
        return address.isLoopbackAddress() && !allowInsecureHttp;
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address inet6)) {
            return false;
        }
        byte[] bytes = inet6.getAddress();
        return (bytes[0] & 0xfe) == 0xfc;
    }

    private static InetAddress unwrapIpv4Mapped(InetAddress address) {
        if (!(address instanceof Inet6Address inet6)) {
            return address;
        }
        byte[] bytes = inet6.getAddress();
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return address;
            }
        }
        if (bytes[10] != (byte) 0xff || bytes[11] != (byte) 0xff) {
            return address;
        }
        try {
            return InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
        } catch (UnknownHostException exception) {
            return address;
        }
    }

    static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "::1".equals(host)
                || host.startsWith("127.");
    }
}
