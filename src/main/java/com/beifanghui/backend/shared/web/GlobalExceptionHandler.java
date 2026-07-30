package com.beifanghui.backend.shared.web;

import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        CommonErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.failure(errorCode.code(), exception.getMessage(), TraceIds.from(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpServletRequest request) {
        CommonErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.failure(errorCode.code(), "请求体不是有效的 JSON", TraceIds.from(request)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        CommonErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        String message;
        if ("resourceId".equals(exception.getName())) {
            message = "resourceId 必须是资源列表 data.items[].id 中的数字，不能使用 traceId 或 accessToken";
        } else if ("skuId".equals(exception.getName())) {
            message = "skuId 必须是资源详情 data.skus[].id 中的数字";
        } else {
            message = "参数 " + exception.getName() + " 的格式不正确";
        }
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.failure(errorCode.code(), message, TraceIds.from(request)));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request) {
        CommonErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.failure(errorCode.code(),
                        "缺少请求头 " + exception.getHeaderName(), TraceIds.from(request)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(HttpServletRequest request) {
        CommonErrorCode errorCode = CommonErrorCode.NOT_FOUND;
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.failure(errorCode.code(), "请求的接口不存在", TraceIds.from(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        String traceId = TraceIds.from(request);
        log.error("Unhandled request error, traceId={}", traceId, exception);
        CommonErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.failure(errorCode.code(), errorCode.message(), traceId));
    }
}
