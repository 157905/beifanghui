package com.beifanghui.backend.refund.api;

import java.time.LocalDateTime;

public record RefundResponse(long id, long orderId, String orderNo, String refundNo,
                             String channelRefundId, long amountCent, String currency,
                             String reason, String status, LocalDateTime refundedAt) {
}
