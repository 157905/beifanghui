package com.beifanghui.backend.scenic.api;

public record AdminScenicTicketResponse(
        long skuId,
        long scenicSpotId,
        String skuCode,
        String name,
        String ticketType,
        long priceCent,
        String currency,
        String status,
        String audienceRule,
        String usageRule,
        String refundRule,
        String entryNotice,
        int validDays,
        Integer maxPerOrder,
        boolean realNameRequired,
        boolean idCardRequired) {
}
