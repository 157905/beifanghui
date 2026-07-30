package com.beifanghui.backend.payment.infrastructure;

import com.beifanghui.backend.payment.application.PaymentGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPaymentGatewayTests {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    void 同一订单和幂等键生成稳定交易号() {
        PaymentGateway.PaymentCommand command =
                new PaymentGateway.PaymentCommand(10L, 8000L, "CNY", "pay-key-1");

        PaymentGateway.PaymentExecution first = gateway.execute(command);
        PaymentGateway.PaymentExecution second = gateway.execute(command);

        assertEquals(first.transactionId(), second.transactionId());
        assertEquals("MOCK", first.channel());
        assertTrue(first.verified());
    }
}
