package com.beifanghui.backend.catalog.api;

import java.time.LocalDate;

/** 新增一个 SKU 在指定日期和时段的可售库存。 */
public record InventorySetupRequest(Long skuId, LocalDate businessDate, String timeSlot,
                                    Integer totalQuantity, Integer availableQuantity, Long priceCent) {
}
