package com.beifanghui.backend.user.api;

import java.time.LocalDateTime;

public record AdminUserSummaryResponse(
        long id,
        String nickname,
        String mobile,
        String status,
        String levelCode,
        int points,
        int growthValue,
        long orderCount,
        long cumulativeSpendCent,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {
}
