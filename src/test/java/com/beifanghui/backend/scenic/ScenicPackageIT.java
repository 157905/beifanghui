package com.beifanghui.backend.scenic;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.order.api.CreateOrderRequest;
import com.beifanghui.backend.order.api.OrderResponse;
import com.beifanghui.backend.order.application.OrderApplicationService;
import com.beifanghui.backend.scenic.api.AdminScenicSpotCreateRequest;
import com.beifanghui.backend.scenic.api.AdminScenicSpotResponse;
import com.beifanghui.backend.scenic.api.AdminScenicTicketCreateRequest;
import com.beifanghui.backend.scenic.api.AdminScenicTicketResponse;
import com.beifanghui.backend.scenic.api.PackageComponentRequest;
import com.beifanghui.backend.scenic.api.PackageComponentUpdateRequest;
import com.beifanghui.backend.scenic.api.ScenicInventorySetupRequest;
import com.beifanghui.backend.scenic.api.ScenicPackageResponse;
import com.beifanghui.backend.scenic.application.AdminScenicManagementService;
import com.beifanghui.backend.scenic.application.ScenicPackageQueryService;
import com.beifanghui.backend.payment.application.MockPaymentService;
import com.beifanghui.backend.verification.api.VerificationTicketResponse;
import com.beifanghui.backend.verification.application.VerificationTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/beifanghui_test?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                "spring.task.scheduling.enabled=false"
        })
@Transactional
class ScenicPackageIT {
    private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(
            "package-admin", "套票运营管理员", "ADMIN", List.of("ROLE_ADMIN"));
    private static final AuthenticatedPrincipal USER = new AuthenticatedPrincipal(
            "package-user", "套票测试用户", "USER", List.of("ROLE_USER"));
    private static final AuthenticatedPrincipal OPS = new AuthenticatedPrincipal(
            "package-ops", "套票核销人员", "OPS", List.of("ROLE_OPS"));

    @Autowired private AdminScenicManagementService managementService;
    @Autowired private ScenicPackageQueryService packageQueryService;
    @Autowired private OrderApplicationService orderService;
    @Autowired private MockPaymentService paymentService;
    @Autowired private VerificationTicketService verificationTicketService;

    @Test
    void 套票按组件签发电子票并可分别核销() {
        AdminScenicSpotResponse spot = managementService.createScenicSpot(ADMIN,
                new AdminScenicSpotCreateRequest("IT_PACKAGE_SCENIC", "套票集成测试景区", "TEST", "自动化测试",
                        null, "测试地址", null, null, "0472-1111111", "自动化测试", "09:00-17:00", 120, "ACTIVE"));
        AdminScenicTicketResponse museumTicket = createTicket(spot.id(), "IT_PACKAGE_MUSEUM_001", "兵器馆参观票", "ADULT", 5000L);
        AdminScenicTicketResponse experienceTicket = createTicket(spot.id(), "IT_PACKAGE_EXPERIENCE_001", "军事体验票", "SERVICE", 7000L);
        AdminScenicTicketResponse packageTicket = createTicket(spot.id(), "IT_PACKAGE_COMBO_001", "兵器馆+军事体验套票", "PACKAGE", 10000L);

        managementService.replacePackageComponents(ADMIN, packageTicket.skuId(), new PackageComponentUpdateRequest(List.of(
                new PackageComponentRequest(museumTicket.skuId(), 1),
                new PackageComponentRequest(experienceTicket.skuId(), 1))));
        managementService.setupInventory(ADMIN, packageTicket.skuId(), LocalDate.now(), "", "it-package-stock-001",
                new ScenicInventorySetupRequest(10, 10000L));

        ScenicPackageResponse packageDetail = packageQueryService.detail(packageTicket.skuId());
        OrderResponse order = orderService.create(USER, "it-package-order-001", new CreateOrderRequest(null, List.of(
                new CreateOrderRequest.Item(packageTicket.skuId(), 1, LocalDate.now(), "", Map.of())), "套票集成测试"));
        paymentService.pay(USER, order.id(), "it-package-payment-001");
        List<VerificationTicketResponse> tickets = verificationTicketService.listOwnedTickets(USER, order.id());

        assertEquals(2, packageDetail.components().size());
        assertEquals("SCENIC_TICKET", order.orderType());
        assertEquals(2, tickets.size());
        assertTrue(tickets.stream().anyMatch(ticket -> "兵器馆参观票".equals(ticket.resourceName())));
        assertTrue(tickets.stream().anyMatch(ticket -> "军事体验票".equals(ticket.resourceName())));

        verificationTicketService.consume(OPS, tickets.get(0).code());
        assertEquals("PAID", orderService.detail(USER, order.id()).status());
        verificationTicketService.consume(OPS, tickets.get(1).code());
        assertEquals("COMPLETED", orderService.detail(USER, order.id()).status());
    }

    private AdminScenicTicketResponse createTicket(long scenicSpotId, String skuCode, String name,
                                                    String ticketType, long priceCent) {
        return managementService.createTicket(ADMIN, scenicSpotId, new AdminScenicTicketCreateRequest(
                skuCode, name, ticketType, priceCent, "ACTIVE", "测试游客", "仅限当天使用", "未核销可退款",
                "请按预约日期入园", 1, 5, false, false));
    }
}
