package com.beifanghui.backend.scenic.api;

import java.time.LocalDate;

public record ScenicInventorySetupResponse(
        long inventoryId,
        long skuId,
        LocalDate businessDate,
        String timeSlot,
        int totalQuantity,
        int reservedQuantity,
        int availableQuantity,
        long priceCent) {
}
