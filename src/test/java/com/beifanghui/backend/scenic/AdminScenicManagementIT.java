package com.beifanghui.backend.scenic;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.order.api.CreateOrderRequest;
import com.beifanghui.backend.order.application.OrderApplicationService;
import com.beifanghui.backend.scenic.api.AdminScenicSpotCreateRequest;
import com.beifanghui.backend.scenic.api.AdminScenicSpotResponse;
import com.beifanghui.backend.scenic.api.AdminScenicTicketCreateRequest;
import com.beifanghui.backend.scenic.api.AdminScenicTicketResponse;
import com.beifanghui.backend.scenic.api.AdminScenicTicketUpdateRequest;
import com.beifanghui.backend.scenic.api.ScenicInventorySetupRequest;
import com.beifanghui.backend.scenic.api.ScenicInventorySetupResponse;
import com.beifanghui.backend.scenic.application.AdminScenicManagementService;
import com.beifanghui.backend.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/beifanghui_test?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                "spring.task.scheduling.enabled=false"
        })
@Transactional
class AdminScenicManagementIT {
    private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(
            "scenic-management-admin", "景区运营管理员", "ADMIN", List.of("ROLE_ADMIN"));
    private static final AuthenticatedPrincipal USER = new AuthenticatedPrincipal(
            "scenic-management-user", "景区测试用户", "USER", List.of("ROLE_USER"));

    @Autowired private AdminScenicManagementService managementService;
    @Autowired private OrderApplicationService orderService;

    @Test
    void 管理员可维护儿童票和预约时段且不能覆盖已预订名额() {
        AdminScenicSpotResponse spot = managementService.createScenicSpot(ADMIN,
                new AdminScenicSpotCreateRequest(
                        "IT_MANAGED_SCENIC", "后台管理测试景区", "CULTURAL_SCENIC", "自动化测试景区",
                        null, "测试地址", null, null, "0472-7654321", "自动化测试使用",
                        "09:00-17:00", 120, "ACTIVE"));
        AdminScenicTicketResponse ticket = managementService.createTicket(ADMIN, spot.id(),
                new AdminScenicTicketCreateRequest(
                        "IT_MANAGED_CHILD_001", "儿童票", "CHILD", 3600L, "ACTIVE", "适用于儿童游客",
                        "仅限预约时段使用", "未核销前可退款", "请按预约时段入园", 1, 5, false, false));
        LocalDate serviceDate = LocalDate.now().plusDays(1);
        ScenicInventorySetupResponse initial = managementService.setupInventory(ADMIN, ticket.skuId(), serviceDate,
                "09:00-12:00", "it-scenic-slot-001", new ScenicInventorySetupRequest(10, 3600L));

        orderService.create(USER, "it-managed-scenic-order", new CreateOrderRequest(null, List.of(
                new CreateOrderRequest.Item(ticket.skuId(), 2, serviceDate, "09:00-12:00", Map.of())), "时段限流测试"));
        ScenicInventorySetupResponse expanded = managementService.setupInventory(ADMIN, ticket.skuId(), serviceDate,
                "09:00-12:00", "it-scenic-slot-002", new ScenicInventorySetupRequest(12, 3800L));
        AdminScenicTicketResponse updatedTicket = managementService.updateTicket(ADMIN, ticket.skuId(),
                new AdminScenicTicketUpdateRequest("儿童票（调整后）", "CHILD", 3800L, "ACTIVE", "适用于儿童游客",
                        "仅限预约时段使用", "未核销前可退款", "请按预约时段入园", 1, 5, false, false));

        assertEquals(10, initial.availableQuantity());
        assertEquals(2, expanded.reservedQuantity());
        assertEquals(10, expanded.availableQuantity());
        assertEquals(3800L, expanded.priceCent());
        assertEquals("儿童票（调整后）", updatedTicket.name());
        assertThrows(BusinessException.class, () -> managementService.setupInventory(ADMIN, ticket.skuId(), serviceDate,
                "09:00-12:00", "it-scenic-slot-003", new ScenicInventorySetupRequest(1, 3800L)));
    }
}
