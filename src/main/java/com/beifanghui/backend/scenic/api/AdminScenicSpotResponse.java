package com.beifanghui.backend.scenic.api;

public record AdminScenicSpotResponse(
        long id,
        long siteId,
        String siteCode,
        String name,
        String categoryCode,
        String status) {
}
