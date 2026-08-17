package dev.ainer.module.knowledge.knowledge.application;

import dev.ainer.core.error.ErrorCode;

/** Knowledge 模块错误码（ADR-0044）：稳定 {@code AINER.KNOWLEDGE.*} 字符串与真实 HTTP 状态码。 */
public enum KnowledgeErrorCode implements ErrorCode {

    OBJECT_NOT_FOUND("AINER.KNOWLEDGE.OBJECT_NOT_FOUND", "知识对象不存在", 404),

    REVISION_NOT_FOUND("AINER.KNOWLEDGE.REVISION_NOT_FOUND", "知识版本不存在", 404),

    INVALID_KIND("AINER.KNOWLEDGE.INVALID_KIND", "kind 必须是 namespaced 受控字符串", 422),

    EMPTY_PAYLOAD("AINER.KNOWLEDGE.EMPTY_PAYLOAD", "语义负载不能为空", 422),

    NOT_PROPOSED("AINER.KNOWLEDGE.NOT_PROPOSED", "只有 PROPOSED 版本可以发布", 409),

    ALREADY_PUBLISHED("AINER.KNOWLEDGE.ALREADY_PUBLISHED", "版本已发布", 409),

    PUBLISH_REQUIRES_HUMAN("AINER.KNOWLEDGE.PUBLISH_REQUIRES_HUMAN",
            "发布是人工门禁：SERVICE/AI 只能创建提案，不能自行发布", 403),

    INVALID_LINEAGE("AINER.KNOWLEDGE.INVALID_LINEAGE", "supersede 基准版本不合法", 422),

    INVALID_PAGE("AINER.KNOWLEDGE.INVALID_PAGE", "分页参数不合法", 422);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    KnowledgeErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
