package com.beifanghui.backend.shared.error;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode {
    INVALID_REQUEST("SYSTEM_400_001", "请求参数不正确", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("AUTH_401_001", "请先登录或重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("AUTH_403_001", "无权访问该端接口", HttpStatus.FORBIDDEN),
    NOT_FOUND("SYSTEM_404_001", "请求的资源不存在", HttpStatus.NOT_FOUND),
    INVENTORY_CONFLICT("INVENTORY_409_001", "库存不足", HttpStatus.CONFLICT),
    ORDER_CONFLICT("ORDER_409_001", "当前订单状态不允许该操作", HttpStatus.CONFLICT),
    PAYMENT_CONFLICT("PAYMENT_409_001", "当前订单不可支付", HttpStatus.CONFLICT),
    REFUND_CONFLICT("REFUND_409_001", "当前订单不可退款", HttpStatus.CONFLICT),
    VERIFICATION_CONFLICT("VERIFICATION_409_001", "核销码当前不可使用", HttpStatus.CONFLICT),
    CONFLICT("SYSTEM_409_001", "当前状态不允许该操作", HttpStatus.CONFLICT),
    RATE_LIMITED("SYSTEM_429_001", "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR("SYSTEM_500_001", "系统暂时无法处理请求", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    CommonErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus status() {
        return status;
    }
}
