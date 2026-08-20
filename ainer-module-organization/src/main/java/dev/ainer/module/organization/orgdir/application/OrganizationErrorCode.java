package dev.ainer.module.organization.orgdir.application;

import dev.ainer.core.error.ErrorCode;

/** 组织目录模块错误码（ADR-0042）：稳定 {@code AINER.ORGANIZATION.*} 字符串与真实 HTTP 状态码。 */
public enum OrganizationErrorCode implements ErrorCode {

    DIRECTORY_NOT_FOUND("AINER.ORGANIZATION.DIRECTORY_NOT_FOUND", "组织目录不存在", 404),

    UNIT_NOT_FOUND("AINER.ORGANIZATION.UNIT_NOT_FOUND", "组织单元不存在", 404),

    ENGAGEMENT_NOT_FOUND("AINER.ORGANIZATION.ENGAGEMENT_NOT_FOUND", "任职关系不存在", 404),

    ASSIGNMENT_NOT_FOUND("AINER.ORGANIZATION.ASSIGNMENT_NOT_FOUND", "任职分配不存在", 404),

    POSITION_NOT_FOUND("AINER.ORGANIZATION.POSITION_NOT_FOUND", "岗位不存在", 404),

    DUPLICATE_DIRECTORY_CODE("AINER.ORGANIZATION.DUPLICATE_DIRECTORY_CODE", "目录编码已存在", 409),

    DUPLICATE_UNIT_CODE("AINER.ORGANIZATION.DUPLICATE_UNIT_CODE", "单元编码已存在", 409),

    DUPLICATE_POSITION_CODE("AINER.ORGANIZATION.DUPLICATE_POSITION_CODE", "岗位编码已存在", 409),

    DUPLICATE_EMPLOYEE_NUMBER("AINER.ORGANIZATION.DUPLICATE_EMPLOYEE_NUMBER", "员工编号已存在", 409),

    ENGAGEMENT_PERIOD_OVERLAP("AINER.ORGANIZATION.ENGAGEMENT_PERIOD_OVERLAP",
            "同一主体在本目录的有效任职期重叠", 409),

    INVALID_STATUS_CHANGE("AINER.ORGANIZATION.INVALID_STATUS_CHANGE", "状态变更不合法", 409),

    OPEN_PRIMARY_CONFLICT("AINER.ORGANIZATION.OPEN_PRIMARY_CONFLICT",
            "该任职已有一个未闭合的主任职分配", 409),

    INVALID_ISSUER("AINER.ORGANIZATION.INVALID_ISSUER", "subject issuer 不在信任范围", 422),

    INVALID_SUBJECT("AINER.ORGANIZATION.INVALID_SUBJECT", "subject 不满足任职要求", 422),

    INVALID_PERIOD("AINER.ORGANIZATION.INVALID_PERIOD", "有效期不合法或不被父关系包含", 422),

    UNIT_MISMATCH("AINER.ORGANIZATION.UNIT_MISMATCH", "岗位与任职分配不属于同一单元或同一任职", 422),


    CONCURRENT_MODIFICATION("AINER.ORGANIZATION.CONCURRENT_MODIFICATION", "任职记录已被并发修改", 409),

    INVALID_PAGE("AINER.ORGANIZATION.INVALID_PAGE", "分页参数不合法", 422);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    OrganizationErrorCode(String code, String defaultMessage, int httpStatus) {
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
