package com.crm.common.exception;

import com.crm.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常（INF-DS-02 §2）
 * 业务规则校验失败时唯一允许的抛出方式；由 GlobalExceptionHandler 全局收口
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String detail) {
        super(detail == null || detail.isBlank() ? resultCode.getMessage() : detail);
        this.code = resultCode.getCode();
    }

    /** 携带自定义业务码（如 ResultCode 之外的业务细分码） */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
