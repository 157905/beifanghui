package com.beifanghui.backend.catalog.api;

import java.time.LocalDate;

/** 运营端库存明细，调整库存前先读取该数据。 */
public record AdminInventoryResponse(
        long inventoryId, long skuId, String skuCode, String skuName, String skuStatus,
        LocalDate businessDate, String timeSlot, int totalQuantity, int availableQuantity, long priceCent) {
}
