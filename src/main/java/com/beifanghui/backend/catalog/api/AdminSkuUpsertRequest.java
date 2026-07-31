package com.beifanghui.backend.catalog.api;

/** 新建或编辑资源 SKU 的管理端输入。 */
public record AdminSkuUpsertRequest(String skuCode, String name, Long priceCent, String status) {
}
