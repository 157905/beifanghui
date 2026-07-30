package com.beifanghui.backend.catalog.api;

import java.time.LocalDate;

public record AvailabilityResponse(Long skuId, LocalDate businessDate, String timeSlot, boolean available,
                                   int totalQuantity, int availableQuantity, Long priceCent,
                                   String currency, boolean inventoryConfigured) {}
