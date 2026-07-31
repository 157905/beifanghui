package com.beifanghui.backend.content.api;

import java.time.LocalDateTime;

public record BannerResponse(
        long id, String title, String imageUrl, String targetType, String targetValue,
        LocalDateTime startAt, LocalDateTime endAt, int sortOrder, boolean enabled,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
