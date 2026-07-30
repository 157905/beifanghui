package com.beifanghui.backend.payment.infrastructure;

import com.beifanghui.backend.payment.application.PaymentGateway;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentExecution execute(PaymentCommand command) {
        return new PaymentExecution(
                "MOCK",
                "MOCK:" + command.orderId() + ":" + command.idempotencyKey(),
                "IDEA_MOCK_PAYMENT",
                true);
    }
}
