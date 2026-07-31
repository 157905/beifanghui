package com.beifanghui.backend.refund.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.refund.application.MockRefundService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("legacy-mock-refund")
@RequestMapping("/api/v1/app/orders")
public class MockRefundController {
    private final MockRefundService service;
    public MockRefundController(MockRefundService service) { this.service = service; }

    @PostMapping("/{orderId}/mock-refund")
    public ApiResponse<RefundResponse> refund(@PathVariable long orderId,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @RequestBody(required = false) MockRefundRequest body,
                                              HttpServletRequest request) {
        return ApiResponse.success("模拟退款成功", service.refund(CurrentPrincipal.from(request), orderId,
                idempotencyKey, body == null ? null : body.reason()), TraceIds.from(request));
    }
}
