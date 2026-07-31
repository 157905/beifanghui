package com.beifanghui.backend.refund.api;

import java.time.LocalDateTime;

public record RefundApplicationResponse(
        long id, long orderId, String orderNo, String refundNo, String channelRefundId,
        long amountCent, String currency, String reason, String status,
        Long requestedBy, Long reviewedBy, String reviewerName, String reviewComment,
        String failureReason, LocalDateTime requestedAt, LocalDateTime reviewedAt, LocalDateTime refundedAt) {
}
