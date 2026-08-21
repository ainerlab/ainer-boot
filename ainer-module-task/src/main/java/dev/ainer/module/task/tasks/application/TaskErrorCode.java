package dev.ainer.module.task.tasks.application;

import dev.ainer.core.error.ErrorCode;

/** 任务模块错误码（ADR-0047）：稳定 {@code AINER.TASK.*} 字符串与真实 HTTP 状态码。 */
public enum TaskErrorCode implements ErrorCode {

    DEFINITION_NOT_FOUND("AINER.TASK.DEFINITION_NOT_FOUND", "任务类型不存在", 404),
    JOB_NOT_FOUND("AINER.TASK.JOB_NOT_FOUND", "任务作业不存在", 404),
    DUPLICATE_TASK_TYPE("AINER.TASK.DUPLICATE_TASK_TYPE", "任务类型已存在", 409),
    DEFINITION_PAUSED("AINER.TASK.DEFINITION_PAUSED", "任务类型已暂停", 409),
    JOB_NOT_RETRYABLE("AINER.TASK.JOB_NOT_RETRYABLE", "只有 FAILED/EXHAUSTED 状态可以重试", 409),
    JOB_NOT_CANCELLABLE("AINER.TASK.JOB_NOT_CANCELLABLE", "只有未完成任务可以取消", 409),
    INVALID_TASK_TYPE("AINER.TASK.INVALID_TASK_TYPE", "任务类型标识不合法", 422),
    INVALID_PAYLOAD("AINER.TASK.INVALID_PAYLOAD", "任务载荷必须是合法 JSON 对象", 422),
    INVALID_INTERVAL("AINER.TASK.INVALID_INTERVAL", "周期间隔必须为正整数秒", 422),
    INVALID_PAGE("AINER.TASK.INVALID_PAGE", "分页参数不合法", 422);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    TaskErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() { return code; }

    @Override
    public String defaultMessage() { return defaultMessage; }

    @Override
    public int httpStatus() { return httpStatus; }
}
