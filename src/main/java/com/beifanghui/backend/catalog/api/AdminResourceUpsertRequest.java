package com.beifanghui.backend.catalog.api;

/** 新建或编辑资源的管理端输入；SKU 价格和库存由独立接口管理。 */
public record AdminResourceUpsertRequest(
        Long siteId, String resourceType, String categoryCode, String name,
        String description, String coverUrl, String status) {
}
