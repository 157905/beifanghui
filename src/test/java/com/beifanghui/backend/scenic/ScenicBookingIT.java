package com.beifanghui.backend.scenic;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.order.api.CreateOrderRequest;
import com.beifanghui.backend.order.api.OrderResponse;
import com.beifanghui.backend.order.application.OrderApplicationService;
import com.beifanghui.backend.payment.application.MockPaymentService;
import com.beifanghui.backend.scenic.api.ScenicSpotDetailResponse;
import com.beifanghui.backend.scenic.api.ScenicVisitorResponse;
import com.beifanghui.backend.scenic.application.ScenicSpotQueryService;
import com.beifanghui.backend.scenic.application.ScenicVisitorQueryService;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.verification.api.VerificationTicketResponse;
import com.beifanghui.backend.verification.application.VerificationTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/beifanghui_test?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                "spring.task.scheduling.enabled=false"
        })
@Transactional
class ScenicBookingIT {
    private static final AuthenticatedPrincipal USER = new AuthenticatedPrincipal(
            "scenic-integration-user", "景区集成测试用户", "USER", List.of("ROLE_USER"));

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ScenicSpotQueryService scenicSpotQueryService;
    @Autowired private OrderApplicationService orderService;
    @Autowired private MockPaymentService paymentService;
    @Autowired private VerificationTicketService ticketService;
    @Autowired private ScenicVisitorQueryService visitorQueryService;

    private long scenicSpotId;
    private long skuId;
    private long inventoryId;

    @BeforeEach
    void 准备景区票种和当天库存() {
        jdbcTemplate.update("""
                INSERT INTO bf_business_site
                (site_code,name,site_type,address,service_phone,introduction,status)
                VALUES ('IT_SCENIC_SITE','集成测试景区','SCENIC','集成测试地址','0472-1234567','仅用于自动化测试','ACTIVE')
                """);
        long siteId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                INSERT INTO bf_resource
                (site_id,resource_type,category_code,name,description,status,attributes)
                VALUES (?,'SCENIC_TICKET','INTEGRATION_TEST','集成测试景区','仅用于自动化测试','ACTIVE',JSON_OBJECT())
                """, siteId);
        scenicSpotId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                INSERT INTO bf_resource_sku
                (resource_id,sku_code,name,price_cent,status,attributes)
                VALUES (?,'IT_SCENIC_ADULT_001','集成测试成人票',6600,'ACTIVE',JSON_OBJECT('ticketType','ADULT'))
                """, scenicSpotId);
        skuId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("""
                INSERT INTO bf_ticket_profile
                (sku_id,audience_rule,usage_rule,refund_rule,entry_notice,valid_days,max_per_order,
                 real_name_required,id_card_required)
                VALUES (?,'成人游客','仅限当天','未核销可退款','携带身份证',1,5,1,1)
                """, skuId);
        jdbcTemplate.update("""
                INSERT INTO bf_inventory
                (sku_id,business_date,time_slot,total_quantity,available_quantity,price_cent)
                VALUES (?,CURRENT_DATE,'',10,10,6600)
                """, skuId);
        inventoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void 可以查询景区详情和当天票种() {
        PageResponse<?> page = scenicSpotQueryService.list("集成测试景区", 1, 10);
        ScenicSpotDetailResponse detail = scenicSpotQueryService.detail(scenicSpotId);
        var tickets = scenicSpotQueryService.tickets(scenicSpotId, LocalDate.now());

        assertEquals(1, page.total());
        assertEquals("集成测试景区", detail.name());
        assertEquals(1, tickets.size());
        assertEquals(6600, tickets.get(0).priceCent());
        assertEquals(10, tickets.get(0).availableQuantity());
        assertTrue(tickets.get(0).realNameRequired());
    }

    @Test
    void 过去日期不能查询或预订() {
        assertThrows(BusinessException.class,
                () -> scenicSpotQueryService.tickets(scenicSpotId, LocalDate.now().minusDays(1)));
    }

    @Test
    void 景区下单支付后按人数签发电子票并保存脱敏游客() {
        Map<String, Object> visitorOne = Map.of(
                "name", "张三", "mobile", "13800138000", "idType", "ID_CARD", "idNo", "150204199001011234");
        Map<String, Object> visitorTwo = Map.of(
                "name", "李四", "mobile", "13900139000", "idType", "ID_CARD", "idNo", "150204199202022345");
        CreateOrderRequest.Item item = new CreateOrderRequest.Item(
                skuId, 2, LocalDate.now(), "", Map.of("visitors", List.of(visitorOne, visitorTwo)));

        OrderResponse order = orderService.create(USER, "it-scenic-order",
                new CreateOrderRequest(null, List.of(item), "景区预订集成测试"));
        paymentService.pay(USER, order.id(), "it-scenic-payment");
        List<VerificationTicketResponse> tickets = ticketService.listOwnedTickets(USER, order.id());
        List<ScenicVisitorResponse> visitors = visitorQueryService.list(USER, order.id());

        assertEquals("SCENIC_TICKET", order.orderType());
        assertEquals(8, availableInventory());
        assertEquals(2, tickets.size());
        assertEquals(2, visitors.size());
        assertEquals("张*", visitors.get(0).maskedName());
        assertEquals("138****8000", visitors.get(0).maskedMobile());
    }

    private int availableInventory() {
        return jdbcTemplate.queryForObject(
                "SELECT available_quantity FROM bf_inventory WHERE id=?", Integer.class, inventoryId);
    }
}
