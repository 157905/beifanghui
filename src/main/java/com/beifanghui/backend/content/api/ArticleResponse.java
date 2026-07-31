package com.beifanghui.backend.content.api;

import java.time.LocalDateTime;

public record ArticleResponse(
        long id, String articleType, String title, String summary, String coverUrl, String content,
        String status, boolean pinned, LocalDateTime publishedAt, Long createdBy,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
