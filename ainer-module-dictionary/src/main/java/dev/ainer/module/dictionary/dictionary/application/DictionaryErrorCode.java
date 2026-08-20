package dev.ainer.module.dictionary.dictionary.application;

import dev.ainer.core.error.ErrorCode;

/**
 * 字典模块错误码（ADR-0040）。稳定的 {@code AINER.DICTIONARY.<ERROR>} 字符串。
 */
public enum DictionaryErrorCode implements ErrorCode {
    TYPE_NOT_FOUND("AINER.DICTIONARY.TYPE_NOT_FOUND", "字典类型不存在", 404),
    TYPE_ALREADY_EXISTS("AINER.DICTIONARY.TYPE_ALREADY_EXISTS", "同父级下已存在相同编码的启用类型", 409),
    PARENT_NOT_FOUND("AINER.DICTIONARY.PARENT_NOT_FOUND", "父级字典类型不存在", 422),
    ITEM_NOT_FOUND("AINER.DICTIONARY.ITEM_NOT_FOUND", "字典项不存在", 404),
    ITEM_ALREADY_EXISTS("AINER.DICTIONARY.ITEM_ALREADY_EXISTS", "同类型下已存在相同编码的启用项", 409),
    INVALID_PAGE("AINER.DICTIONARY.INVALID_PAGE", "分页参数不合法", 422),
    INVALID_REQUEST("AINER.DICTIONARY.INVALID_REQUEST", "请求参数不合法", 400),
    CONCURRENT_MODIFICATION("AINER.DICTIONARY.CONCURRENT_MODIFICATION", "字典对象已被并发修改", 409);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    DictionaryErrorCode(String code, String defaultMessage, int httpStatus) {
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
