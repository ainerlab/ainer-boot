package dev.ainer.module.ai.agent.application;

import dev.ainer.core.error.ErrorCode;

/** Agent 注册表错误码（ADR-0043 A1）：稳定 {@code AINER.AI_AGENT.*} 字符串。 */
public enum AiAgentErrorCode implements ErrorCode {

    AGENT_NOT_FOUND("AINER.AI_AGENT.AGENT_NOT_FOUND", "Agent 不存在", 404),

    CODE_VERSION_CONFLICT("AINER.AI_AGENT.CODE_VERSION_CONFLICT", "Agent code+version 已存在", 409),

    ALREADY_RETIRED("AINER.AI_AGENT.ALREADY_RETIRED", "Agent 已退役", 409),

    INVALID_DEFINITION("AINER.AI_AGENT.INVALID_DEFINITION", "Agent 定义参数不合法", 422);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    AiAgentErrorCode(String code, String defaultMessage, int httpStatus) {
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
