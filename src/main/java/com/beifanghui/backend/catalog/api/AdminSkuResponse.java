package com.beifanghui.backend.catalog.api;

/** 运营端 SKU 信息，价格统一使用分，避免浮点金额误差。 */
public record AdminSkuResponse(long id, long resourceId, String skuCode, String name,
                               long priceCent, String status, String attributes) {
}
