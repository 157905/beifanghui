package com.beifanghui.backend.order.extension;

import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrderBusinessExtensionRegistry {
    private final Map<String, OrderBusinessExtension> extensions = new HashMap<>();

    public OrderBusinessExtensionRegistry(List<OrderBusinessExtension> candidates) {
        for (OrderBusinessExtension extension : candidates) {
            OrderBusinessExtension previous = extensions.put(extension.resourceType(), extension);
            if (previous != null) {
                throw new IllegalStateException("资源类型存在重复订单扩展：" + extension.resourceType());
            }
        }
    }

    public void validate(OrderBusinessContext context) {
        OrderBusinessExtension extension = extensions.get(context.resourceType());
        if (extension != null) extension.validate(context);
        else if (context.businessData() != null && !context.businessData().isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                    "资源类型" + context.resourceType() + "暂不支持businessData");
        }
    }

    public void save(long orderId, long orderItemId, OrderBusinessContext context) {
        OrderBusinessExtension extension = extensions.get(context.resourceType());
        if (extension != null) extension.save(orderId, orderItemId, context);
    }
}
