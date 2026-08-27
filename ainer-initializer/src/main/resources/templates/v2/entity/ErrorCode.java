package {{package.name}}.{{entity.package}}.application;

import dev.ainer.core.error.ErrorCode;

/** 产品自有稳定错误；消费者错误码不复用 AINER 命名空间。 */
public enum {{entity.className}}ErrorCode implements ErrorCode {

    INVALID_PAGE("{{project.errorNamespace}}.{{entity.errorSegment}}.INVALID_PAGE", "分页参数不正确", 400),
    ACCESS_DENIED("{{project.errorNamespace}}.{{entity.errorSegment}}.ACCESS_DENIED", "无权访问该资源", 403),
    NOT_FOUND("{{project.errorNamespace}}.{{entity.errorSegment}}.NOT_FOUND", "资源不存在", 404),
    CONCURRENT_MODIFICATION("{{project.errorNamespace}}.{{entity.errorSegment}}.CONCURRENT_MODIFICATION",
            "资源已被其他请求修改", 409);

    private final String code;
    private final String message;
    private final int status;

    {{entity.className}}ErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return message;
    }

    @Override
    public int httpStatus() {
        return status;
    }
}
