package dev.ainer.authorizationserver.login;

public enum AinerLoginPageState {

    NORMAL("normal", null, null, null),
    CREDENTIAL_ERROR(
            "credential-error",
            "error",
            "登录失败",
            "用户名或密码错误，请重新输入。"),
    RATE_LIMITED(
            "rate-limited",
            "warning",
            "请稍后再试",
            "登录尝试过于频繁，请稍后再试。"),
    SERVICE_UNAVAILABLE(
            "service-unavailable",
            "error",
            "登录服务暂时不可用",
            "请稍后重试。");

    private final String key;
    private final String severity;
    private final String title;
    private final String message;

    AinerLoginPageState(
            String key,
            String severity,
            String title,
            String message) {
        this.key = key;
        this.severity = severity;
        this.title = title;
        this.message = message;
    }

    public String key() {
        return key;
    }

    public String severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }
}
