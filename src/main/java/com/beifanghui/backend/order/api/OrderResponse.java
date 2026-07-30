package com.beifanghui.backend.order.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id, String orderNo, String orderType, String status,
                            long totalAmountCent, long payableAmountCent, long paidAmountCent,
                            String currency, LocalDateTime expiresAt, LocalDateTime createdAt,
                            LocalDateTime cancelledAt, String remark, List<Item> items) {
    public record Item(Long id, Long skuId, String skuCode, String skuName, String resourceName,
                       String resourceType, int quantity, long unitPriceCent, long amountCent,
                       LocalDate serviceDate, String timeSlot) {}
}
