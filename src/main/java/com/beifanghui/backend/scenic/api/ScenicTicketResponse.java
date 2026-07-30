package com.beifanghui.backend.scenic.api;

import java.time.LocalDate;

public record ScenicTicketResponse(
        long skuId,
        String skuCode,
        String name,
        LocalDate serviceDate,
        String timeSlot,
        long priceCent,
        String currency,
        boolean available,
        int availableQuantity,
        boolean inventoryConfigured,
        String audienceRule,
        String usageRule,
        String refundRule,
        String entryNotice,
        int validDays,
        Integer maxPerOrder,
        boolean realNameRequired,
        boolean idCardRequired,
        String attributes) {
}
