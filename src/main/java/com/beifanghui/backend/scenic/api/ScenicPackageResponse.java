package com.beifanghui.backend.scenic.api;

import java.util.List;

public record ScenicPackageResponse(
        long packageSkuId,
        long packageId,
        String packageCode,
        String name,
        String description,
        long priceCent,
        String currency,
        String status,
        List<Component> components) {
    public record Component(
            long skuId,
            String skuCode,
            String skuName,
            String resourceName,
            String resourceType,
            int quantity) {
    }
}
