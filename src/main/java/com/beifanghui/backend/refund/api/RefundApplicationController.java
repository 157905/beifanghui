package com.beifanghui.backend.refund.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.refund.application.RefundApplicationService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class RefundApplicationController {
    private final RefundApplicationService service;
    public RefundApplicationController(RefundApplicationService service) { this.service = service; }

    @PostMapping("/api/v1/app/orders/{orderId}/refund-applications")
    public ApiResponse<RefundApplicationResponse> apply(@PathVariable long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RefundApplicationRequest body, HttpServletRequest request) {
        return ApiResponse.success("退款申请已提交", service.apply(CurrentPrincipal.from(request), orderId,
                idempotencyKey, body == null ? null : body.reason()), TraceIds.from(request));
    }

    @GetMapping("/api/v1/app/refund-applications")
    public ApiResponse<PageResponse<RefundApplicationResponse>> mine(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize, HttpServletRequest request) {
        return ApiResponse.success(service.listMine(CurrentPrincipal.from(request), page, pageSize), TraceIds.from(request));
    }

    @GetMapping("/api/v1/app/refund-applications/{refundId}")
    public ApiResponse<RefundApplicationResponse> mineDetail(@PathVariable long refundId, HttpServletRequest request) {
        return ApiResponse.success(service.detailMine(CurrentPrincipal.from(request), refundId), TraceIds.from(request));
    }

    @GetMapping("/api/v1/admin/refund-applications")
    public ApiResponse<PageResponse<RefundApplicationResponse>> adminList(@RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(service.listAdmin(status, page, pageSize), TraceIds.from(request));
    }

    @GetMapping("/api/v1/admin/refund-applications/{refundId}")
    public ApiResponse<RefundApplicationResponse> adminDetail(@PathVariable long refundId, HttpServletRequest request) {
        return ApiResponse.success(service.detailAdmin(refundId), TraceIds.from(request));
    }

    @PostMapping("/api/v1/admin/refund-applications/{refundId}/approve")
    public ApiResponse<RefundApplicationResponse> approve(@PathVariable long refundId,
            @RequestBody(required=false) RefundReviewRequest body, HttpServletRequest request) {
        return ApiResponse.success("退款审核通过，等待渠道处理", service.approve(CurrentPrincipal.from(request), refundId,
                body == null ? null : body.comment()), TraceIds.from(request));
    }

    @PostMapping("/api/v1/admin/refund-applications/{refundId}/reject")
    public ApiResponse<RefundApplicationResponse> reject(@PathVariable long refundId,
            @RequestBody RefundReviewRequest body, HttpServletRequest request) {
        return ApiResponse.success("退款申请已拒绝", service.reject(CurrentPrincipal.from(request), refundId,
                body == null ? null : body.comment()), TraceIds.from(request));
    }

    @PostMapping("/api/v1/callbacks/refunds/mock")
    public ApiResponse<RefundApplicationResponse> mockCallback(@RequestHeader("X-Mock-Callback-Key") String callbackKey,
            @RequestBody MockRefundCallbackRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("退款回调处理成功", service.callback(callbackKey, body), TraceIds.from(request));
    }
}
