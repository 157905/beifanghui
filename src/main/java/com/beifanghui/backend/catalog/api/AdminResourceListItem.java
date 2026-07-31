package com.beifanghui.backend.catalog.api;

import java.time.LocalDateTime;

/** 运营端资源列表行，不向小程序端复用，避免暴露草稿和下架数据。 */
public record AdminResourceListItem(
        long id, Long siteId, String resourceType, String categoryCode, String name,
        String description, String coverUrl, String status, long skuCount, LocalDateTime updatedAt) {
}
