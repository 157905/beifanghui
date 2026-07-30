package com.beifanghui.backend.payment.api;

import java.time.LocalDateTime;

public record PaymentResponse(Long id, Long orderId, String orderNo, String paymentNo,
                              String channel, String transactionId, long amountCent,
                              String currency, String status, LocalDateTime paidAt) {}
