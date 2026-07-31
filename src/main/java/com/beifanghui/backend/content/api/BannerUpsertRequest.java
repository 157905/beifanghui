package com.beifanghui.backend.content.api;

import java.time.LocalDateTime;

public record BannerUpsertRequest(
        String title, String imageUrl, String targetType, String targetValue,
        LocalDateTime startAt, LocalDateTime endAt, Integer sortOrder, Boolean enabled) {
}
