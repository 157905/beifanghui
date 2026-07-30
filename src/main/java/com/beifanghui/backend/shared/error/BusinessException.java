package com.beifanghui.backend.shared.error;

public class BusinessException extends RuntimeException {

    private final CommonErrorCode errorCode;

    public BusinessException(CommonErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(CommonErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CommonErrorCode errorCode() {
        return errorCode;
    }
}
