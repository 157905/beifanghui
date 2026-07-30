package com.beifanghui.backend.catalog.api;

import java.time.LocalDate;

public record InventoryAdjustmentResponse(long inventoryId, long skuId, LocalDate businessDate,
                                          String timeSlot, int totalQuantity, int previousQuantity,
                                          int availableQuantity, int delta) {}
