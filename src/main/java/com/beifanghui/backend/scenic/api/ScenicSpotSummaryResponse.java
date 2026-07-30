package com.beifanghui.backend.scenic.api;

public record ScenicSpotSummaryResponse(
        long id,
        long siteId,
        String name,
        String categoryCode,
        String description,
        String coverUrl,
        String address,
        String servicePhone,
        Long minimumPriceCent,
        String currency) {
}
