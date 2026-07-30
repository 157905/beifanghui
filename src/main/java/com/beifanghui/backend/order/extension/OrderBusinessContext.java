package com.beifanghui.backend.order.extension;

import java.time.LocalDate;
import java.util.Map;

public record OrderBusinessContext(long skuId, String resourceType, int quantity,
                                   LocalDate serviceDate, String timeSlot,
                                   Map<String, Object> businessData) {}
