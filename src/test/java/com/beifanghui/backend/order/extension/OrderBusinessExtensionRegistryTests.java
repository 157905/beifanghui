package com.beifanghui.backend.order.extension;

import com.beifanghui.backend.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderBusinessExtensionRegistryTests {
    @Test
    void 应把校验和保存请求路由给对应模块() {
        RecordingExtension extension = new RecordingExtension("SCENIC_TICKET");
        OrderBusinessExtensionRegistry registry = new OrderBusinessExtensionRegistry(List.of(extension));
        OrderBusinessContext context = context("SCENIC_TICKET", Map.of("visitors", List.of()));

        registry.validate(context);
        registry.save(10L, 20L, context);

        assertEquals(1, extension.validateCount);
        assertEquals(1, extension.saveCount);
        assertEquals(10L, extension.orderId);
        assertEquals(20L, extension.orderItemId);
    }

    @Test
    void 未注册模块不能提交无法处理的业务数据() {
        OrderBusinessExtensionRegistry registry = new OrderBusinessExtensionRegistry(List.of());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> registry.validate(context("UNKNOWN", Map.of("value", "test"))));
        assertEquals("SYSTEM_400_001", exception.errorCode().code());
    }

    @Test
    void 同一资源类型不能注册两个扩展() {
        assertThrows(IllegalStateException.class, () -> new OrderBusinessExtensionRegistry(List.of(
                new RecordingExtension("HOTEL_ROOM"), new RecordingExtension("HOTEL_ROOM"))));
    }

    private OrderBusinessContext context(String type, Map<String,Object> data) {
        return new OrderBusinessContext(5L,type,1,LocalDate.of(2026,7,30),"",data);
    }

    private static final class RecordingExtension implements OrderBusinessExtension {
        private final String type;
        private int validateCount;
        private int saveCount;
        private long orderId;
        private long orderItemId;
        private RecordingExtension(String type){this.type=type;}
        public String resourceType(){return type;}
        public void validate(OrderBusinessContext context){validateCount++;}
        public void save(long orderId,long orderItemId,OrderBusinessContext context){
            saveCount++;this.orderId=orderId;this.orderItemId=orderItemId;
        }
    }
}
