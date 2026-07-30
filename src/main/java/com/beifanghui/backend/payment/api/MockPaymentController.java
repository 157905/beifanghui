package com.beifanghui.backend.payment.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.payment.application.MockPaymentService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/orders")
public class MockPaymentController {
    private final MockPaymentService paymentService;

    public MockPaymentController(MockPaymentService paymentService) { this.paymentService = paymentService; }

    @PostMapping("/{orderId}/mock-pay")
    public ApiResponse<PaymentResponse> mockPay(@PathVariable long orderId,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                HttpServletRequest request) {
        return ApiResponse.success("模拟支付成功",
                paymentService.pay(CurrentPrincipal.from(request), orderId, idempotencyKey), TraceIds.from(request));
    }
}
