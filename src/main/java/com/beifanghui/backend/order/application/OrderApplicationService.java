package com.beifanghui.backend.order.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.order.api.CreateOrderRequest;
import com.beifanghui.backend.order.api.OrderResponse;
import com.beifanghui.backend.order.extension.OrderBusinessContext;
import com.beifanghui.backend.order.extension.OrderBusinessExtensionRegistry;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderApplicationService {
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final JdbcTemplate jdbcTemplate;
    private final OrderBusinessExtensionRegistry extensionRegistry;

    public OrderApplicationService(JdbcTemplate jdbcTemplate, OrderBusinessExtensionRegistry extensionRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.extensionRegistry = extensionRegistry;
    }

    @Transactional
    public OrderResponse create(AuthenticatedPrincipal principal, String idempotencyKey, CreateOrderRequest request) {
        validateCreateRequest(idempotencyKey, request);
        long userId = ensureDatabaseUser(principal);
        OrderResponse existing = findByClientRequestId(userId, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        String orderNo = nextOrderNo();
        List<PricedItem> pricedItems = new ArrayList<>();
        long totalAmount = 0;
        for (int index = 0; index < request.items().size(); index++) {
            CreateOrderRequest.Item item = request.items().get(index);
            PricedItem priced = lockAndPrice(item);
            pricedItems.add(priced);
            extensionRegistry.validate(toBusinessContext(item, priced));
            totalAmount = Math.addExact(totalAmount, Math.multiplyExact(priced.priceCent(), item.quantity().longValue()));
        }

        // 并发重复请求可能在上面的库存行锁处等待；获得锁后必须再次检查幂等结果。
        existing = findByClientRequestId(userId, idempotencyKey);
        if (existing != null) {
            return existing;
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        long orderId = insertOrder(userId, idempotencyKey, orderNo, deriveOrderType(pricedItems),
                totalAmount, expiresAt, normalizeRemark(request.remark()));

        for (int index = 0; index < request.items().size(); index++) {
            CreateOrderRequest.Item item = request.items().get(index);
            PricedItem priced = pricedItems.get(index);
            int changed = jdbcTemplate.update("""
                    UPDATE bf_inventory
                    SET available_quantity = available_quantity - ?, version = version + 1
                    WHERE id = ? AND available_quantity >= ?
                    """, item.quantity(), priced.inventoryId(), item.quantity());
            if (changed != 1) {
                throw new BusinessException(CommonErrorCode.INVENTORY_CONFLICT,
                        priced.skuName() + " 库存不足");
            }
            int quantityAfter = priced.availableQuantity() - item.quantity();
            jdbcTemplate.update("""
                    INSERT INTO bf_inventory_log
                    (inventory_id, order_no, change_type, quantity_delta, quantity_after, idempotency_key, remark)
                    VALUES (?, ?, 'ORDER_LOCK', ?, ?, ?, '创建待支付订单锁定库存')
                    """, priced.inventoryId(), orderNo, -item.quantity(), quantityAfter,
                    idempotencyKey + ":LOCK:" + index);
            long orderItemId = insertOrderItem(orderId, item, priced);
            extensionRegistry.save(orderId, orderItemId, toBusinessContext(item, priced));
        }
        return findOwnedOrder(userId, orderId);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(AuthenticatedPrincipal principal, int page, int pageSize) {
        validatePage(page, pageSize);
        long userId = requireDatabaseUser(principal);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_order WHERE user_id = ?", Long.class, userId);
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM bf_order WHERE user_id = ? ORDER BY id DESC LIMIT ? OFFSET ?
                """, Long.class, userId, pageSize, (page - 1) * pageSize);
        List<OrderResponse> items = ids.stream().map(id -> findOwnedOrder(userId, id)).toList();
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public OrderResponse detail(AuthenticatedPrincipal principal, long orderId) {
        return findOwnedOrder(requireDatabaseUser(principal), orderId);
    }

    @Transactional
    public OrderResponse cancel(AuthenticatedPrincipal principal, long orderId) {
        long userId = requireDatabaseUser(principal);
        OrderHead head = queryOrderHeadForUpdate(userId, orderId);
        if (!PENDING_PAYMENT.equals(head.status())) {
            throw new BusinessException(CommonErrorCode.ORDER_CONFLICT,
                    "只有待支付订单可以取消，当前状态为 " + head.status());
        }
        List<CancelItem> items = jdbcTemplate.query("""
                SELECT id, sku_id, quantity, service_date, COALESCE(time_slot, '') time_slot
                FROM bf_order_item WHERE order_id = ? ORDER BY id
                """, (rs, rowNum) -> new CancelItem(rs.getLong("id"), rs.getLong("sku_id"),
                rs.getInt("quantity"), rs.getDate("service_date").toLocalDate(), rs.getString("time_slot")), orderId);
        for (CancelItem item : items) {
            InventoryForCancel inventory = jdbcTemplate.queryForObject("""
                    SELECT id, available_quantity FROM bf_inventory
                    WHERE sku_id = ? AND business_date = ? AND time_slot = ? FOR UPDATE
                    """, (rs, rowNum) -> new InventoryForCancel(rs.getLong("id"), rs.getInt("available_quantity")),
                    item.skuId(), item.serviceDate(), item.timeSlot());
            int quantityAfter = inventory.availableQuantity() + item.quantity();
            jdbcTemplate.update("""
                    UPDATE bf_inventory SET available_quantity = ?, version = version + 1 WHERE id = ?
                    """, quantityAfter, inventory.id());
            jdbcTemplate.update("""
                    INSERT INTO bf_inventory_log
                    (inventory_id, order_no, change_type, quantity_delta, quantity_after, idempotency_key, remark)
                    VALUES (?, ?, 'ORDER_RELEASE', ?, ?, ?, '取消待支付订单释放库存')
                    """, inventory.id(), head.orderNo(), item.quantity(), quantityAfter,
                    "cancel:" + head.orderNo() + ":" + item.id());
        }
        jdbcTemplate.update("""
                UPDATE bf_order SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE id = ?
                """, orderId);
        return findOwnedOrder(userId, orderId);
    }

    private PricedItem lockAndPrice(CreateOrderRequest.Item item) {
        List<PricedItem> rows = jdbcTemplate.query("""
                SELECT i.id inventory_id, i.available_quantity, i.price_cent,
                       s.id sku_id, s.sku_code, s.name sku_name,
                       r.name resource_name, r.resource_type
                FROM bf_inventory i
                JOIN bf_resource_sku s ON s.id = i.sku_id AND s.status = 'ACTIVE'
                JOIN bf_resource r ON r.id = s.resource_id AND r.status = 'ACTIVE'
                WHERE i.sku_id = ? AND i.business_date = ? AND i.time_slot = ?
                FOR UPDATE
                """, pricedItemMapper(), item.skuId(), item.serviceDate(), normalizeSlot(item.timeSlot()));
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "没有找到对应日期和时段的可售库存");
        }
        PricedItem priced = rows.get(0);
        if (priced.availableQuantity() < item.quantity()) {
            throw new BusinessException(CommonErrorCode.INVENTORY_CONFLICT,
                    priced.skuName() + " 库存不足，当前可用 " + priced.availableQuantity());
        }
        return priced;
    }

    private RowMapper<PricedItem> pricedItemMapper() {
        return (rs, rowNum) -> new PricedItem(rs.getLong("inventory_id"), rs.getInt("available_quantity"),
                rs.getLong("price_cent"), rs.getLong("sku_id"), rs.getString("sku_code"),
                rs.getString("sku_name"), rs.getString("resource_name"), rs.getString("resource_type"));
    }

    private long insertOrder(long userId, String clientRequestId, String orderNo, String orderType,
                             long amount, LocalDateTime expiresAt, String remark) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_order
                    (order_no, user_id, client_request_id, order_type, status,
                     total_amount_cent, discount_amount_cent, payable_amount_cent, paid_amount_cent,
                     expires_at, remark)
                    VALUES (?, ?, ?, ?, 'PENDING_PAYMENT', ?, 0, ?, 0, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, orderNo);
            statement.setLong(2, userId);
            statement.setString(3, clientRequestId);
            statement.setString(4, orderType);
            statement.setLong(5, amount);
            statement.setLong(6, amount);
            statement.setObject(7, expiresAt);
            statement.setString(8, remark);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private long insertOrderItem(long orderId, CreateOrderRequest.Item item, PricedItem priced) {
        long amount = Math.multiplyExact(priced.priceCent(), item.quantity().longValue());
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_order_item
                    (order_id, sku_id, sku_code, sku_name, resource_name, resource_type,
                     quantity, unit_price_cent, amount_cent, service_date, time_slot, snapshot)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, JSON_OBJECT('priceSource', 'INVENTORY'))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, orderId);
            statement.setLong(2, priced.skuId());
            statement.setString(3, priced.skuCode());
            statement.setString(4, priced.skuName());
            statement.setString(5, priced.resourceName());
            statement.setString(6, priced.resourceType());
            statement.setInt(7, item.quantity());
            statement.setLong(8, priced.priceCent());
            statement.setLong(9, amount);
            statement.setObject(10, item.serviceDate());
            statement.setString(11, normalizeSlot(item.timeSlot()));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private OrderBusinessContext toBusinessContext(CreateOrderRequest.Item item, PricedItem priced) {
        return new OrderBusinessContext(priced.skuId(), priced.resourceType(), item.quantity(),
                item.serviceDate(), normalizeSlot(item.timeSlot()), item.businessData());
    }

    private long ensureDatabaseUser(AuthenticatedPrincipal principal) {
        String openid = mockOpenid(principal);
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM bf_user WHERE wechat_openid = ?", Long.class, openid);
        if (!ids.isEmpty()) return ids.get(0);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_user (wechat_openid, nickname, real_name, status, last_login_at)
                    VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, openid);
            statement.setString(2, principal.displayName());
            statement.setString(3, principal.displayName());
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private long requireDatabaseUser(AuthenticatedPrincipal principal) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM bf_user WHERE wechat_openid = ?",
                Long.class, mockOpenid(principal));
        if (ids.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "当前用户尚未创建订单记录");
        }
        return ids.get(0);
    }

    private String mockOpenid(AuthenticatedPrincipal principal) {
        return principal.databaseOpenId();
    }

    private OrderResponse findByClientRequestId(long userId, String idempotencyKey) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM bf_order WHERE user_id = ? AND client_request_id = ?
                """, Long.class, userId, idempotencyKey);
        return ids.isEmpty() ? null : findOwnedOrder(userId, ids.get(0));
    }

    private OrderResponse findOwnedOrder(long userId, long orderId) {
        List<OrderResponse> orders = jdbcTemplate.query("""
                SELECT id, order_no, order_type, status, total_amount_cent, payable_amount_cent,
                       paid_amount_cent, expires_at, created_at, cancelled_at, remark
                FROM bf_order WHERE id = ? AND user_id = ?
                """, (rs, rowNum) -> new OrderResponse(rs.getLong("id"), rs.getString("order_no"),
                rs.getString("order_type"), rs.getString("status"), rs.getLong("total_amount_cent"),
                rs.getLong("payable_amount_cent"), rs.getLong("paid_amount_cent"), "CNY",
                rs.getObject("expires_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("cancelled_at", LocalDateTime.class), rs.getString("remark"), List.of()), orderId, userId);
        if (orders.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在或无权访问");
        }
        OrderResponse head = orders.get(0);
        List<OrderResponse.Item> items = jdbcTemplate.query("""
                SELECT id, sku_id, sku_code, sku_name, resource_name, resource_type, quantity,
                       unit_price_cent, amount_cent, service_date, COALESCE(time_slot, '') time_slot
                FROM bf_order_item WHERE order_id = ? ORDER BY id
                """, (rs, rowNum) -> new OrderResponse.Item(rs.getLong("id"), rs.getLong("sku_id"),
                rs.getString("sku_code"), rs.getString("sku_name"), rs.getString("resource_name"),
                rs.getString("resource_type"), rs.getInt("quantity"), rs.getLong("unit_price_cent"),
                rs.getLong("amount_cent"), rs.getDate("service_date").toLocalDate(), rs.getString("time_slot")), orderId);
        return new OrderResponse(head.id(), head.orderNo(), head.orderType(), head.status(), head.totalAmountCent(),
                head.payableAmountCent(), head.paidAmountCent(), head.currency(), head.expiresAt(), head.createdAt(),
                head.cancelledAt(), head.remark(), items);
    }

    private OrderHead queryOrderHeadForUpdate(long userId, long orderId) {
        List<OrderHead> rows = jdbcTemplate.query("""
                SELECT order_no, status FROM bf_order WHERE id = ? AND user_id = ? FOR UPDATE
                """, (rs, rowNum) -> new OrderHead(rs.getString("order_no"), rs.getString("status")), orderId, userId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在或无权访问");
        return rows.get(0);
    }

    private void validateCreateRequest(String idempotencyKey, CreateOrderRequest request) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "Idempotency-Key 不能为空且最长64字符");
        }
        if (request == null || request.items() == null || request.items().isEmpty() || request.items().size() > 20) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "订单明细数量范围为1—20");
        }
        Set<String> uniqueItems = new HashSet<>();
        for (CreateOrderRequest.Item item : request.items()) {
            if (item == null || item.skuId() == null || item.skuId() <= 0 || item.serviceDate() == null
                    || item.quantity() == null || item.quantity() < 1 || item.quantity() > 99) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "SKU、服务日期和数量不合法");
            }
            String unique = item.skuId() + "|" + item.serviceDate() + "|" + normalizeSlot(item.timeSlot());
            if (!uniqueItems.add(unique)) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "同一SKU、日期和时段不能重复提交");
            }
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page 从1开始，pageSize范围为1—100");
        }
    }

    private String deriveOrderType(List<PricedItem> pricedItems) {
        Set<String> resourceTypes = pricedItems.stream()
                .map(PricedItem::resourceType)
                .collect(java.util.stream.Collectors.toSet());
        return resourceTypes.size() == 1 ? resourceTypes.iterator().next() : "MIXED";
    }
    private String normalizeSlot(String slot) { return slot == null ? "" : slot.trim(); }
    private String normalizeRemark(String remark) {
        if (!StringUtils.hasText(remark)) return null;
        String value = remark.trim();
        if (value.length() > 500) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "remark最长500字符");
        return value;
    }
    private String nextOrderNo() {
        return "BFH" + LocalDateTime.now().format(ORDER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private record PricedItem(long inventoryId, int availableQuantity, long priceCent, long skuId,
                              String skuCode, String skuName, String resourceName, String resourceType) {}
    private record CancelItem(long id, long skuId, int quantity, java.time.LocalDate serviceDate, String timeSlot) {}
    private record InventoryForCancel(long id, int availableQuantity) {}
    private record OrderHead(String orderNo, String status) {}
}
