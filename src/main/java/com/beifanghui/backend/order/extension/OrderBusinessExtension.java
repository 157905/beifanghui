package com.beifanghui.backend.order.extension;

public interface OrderBusinessExtension {
    String resourceType();
    void validate(OrderBusinessContext context);
    void save(long orderId, long orderItemId, OrderBusinessContext context);
}
