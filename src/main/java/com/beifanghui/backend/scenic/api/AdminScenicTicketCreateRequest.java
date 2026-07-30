package com.beifanghui.backend.scenic.api;

public record AdminScenicTicketCreateRequest(
        String skuCode,
        String name,
        String ticketType,
        Long priceCent,
        String status,
        String audienceRule,
        String usageRule,
        String refundRule,
        String entryNotice,
        Integer validDays,
        Integer maxPerOrder,
        Boolean realNameRequired,
        Boolean idCardRequired) {
}
