package com.beifanghui.backend.scenic.api;

import java.math.BigDecimal;

public record AdminScenicSpotCreateRequest(
        String siteCode,
        String name,
        String categoryCode,
        String description,
        String coverUrl,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String servicePhone,
        String introduction,
        String openingHours,
        Integer recommendedDurationMinutes,
        String status) {
}
