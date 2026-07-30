package com.beifanghui.backend.payment.application;

public interface PaymentGateway {

    PaymentExecution execute(PaymentCommand command);

    record PaymentCommand(long orderId, long amountCent, String currency, String idempotencyKey) {
    }

    record PaymentExecution(String channel, String transactionId, String source, boolean verified) {
    }
}
