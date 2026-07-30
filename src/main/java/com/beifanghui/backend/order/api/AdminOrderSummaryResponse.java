package com.beifanghui.backend.order.api;

import java.time.LocalDateTime;

public record AdminOrderSummaryResponse(long id, String orderNo, long userId, String userNickname,
                                        String orderType, String status, long payableAmountCent,
                                        long paidAmountCent, String currency, LocalDateTime expiresAt,
                                        LocalDateTime createdAt) {
}
