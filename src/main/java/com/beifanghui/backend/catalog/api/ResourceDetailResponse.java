package com.beifanghui.backend.catalog.api;

import java.util.List;

public record ResourceDetailResponse(Long id, Long siteId, String resourceType, String categoryCode,
                                     String name, String description, String coverUrl, String attributes,
                                     List<SkuResponse> skus) {
    public record SkuResponse(Long id, String skuCode, String name, Long priceCent,
                              String currency, String attributes) {}
}
