package com.beifanghui.backend.scenic.api;

import java.time.LocalDateTime;

public record ScenicGuideResponse(
        long id,
        long scenicSpotId,
        String contentType,
        String title,
        String summary,
        String coverUrl,
        String contentUrl,
        String contentText,
        Integer durationSeconds,
        int sortOrder,
        String status,
        LocalDateTime updatedAt) {
}
