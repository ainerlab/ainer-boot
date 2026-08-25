package dev.ainer.module.notification.notification.application;

import java.util.Objects;

/**
 * 邮件地址与主题的失败关闭校验：拒绝缺段、空白、控制字符和 CR/LF，降低头注入。
 * 异常消息不含地址原文。
 */
public final class EmailAddressRules {

    private static final int MAX_ADDRESS_LENGTH = 320;
    private static final int MAX_TEXT_LENGTH = 2000;

    private EmailAddressRules() {
    }

    public static String validate(String address) {
        if (address == null || address.isBlank() || address.length() > MAX_ADDRESS_LENGTH) {
            throw new IllegalArgumentException("Email address is not allowed");
        }
        String value = address.strip();
        if (containsCtl(value) || containsWhitespace(value)
                || value.indexOf('@') <= 0 || value.indexOf('@') != value.lastIndexOf('@')) {
            throw new IllegalArgumentException("Email address is not allowed");
        }
        int at = value.indexOf('@');
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        if (local.isBlank() || domain.isBlank() || !domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            throw new IllegalArgumentException("Email address is not allowed");
        }
        return value;
    }

    public static String requireSafeText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() > MAX_TEXT_LENGTH || containsCtl(text)) {
            throw new IllegalArgumentException("Email content is not allowed");
        }
        return text;
    }

    private static boolean containsCtl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c == 127) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
