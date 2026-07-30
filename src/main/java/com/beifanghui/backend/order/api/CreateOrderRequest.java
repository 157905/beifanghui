package com.beifanghui.backend.order.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CreateOrderRequest(String orderType, List<Item> items, String remark) {
    public record Item(Long skuId, Integer quantity, LocalDate serviceDate, String timeSlot,
                       Map<String, Object> businessData) {}
}
