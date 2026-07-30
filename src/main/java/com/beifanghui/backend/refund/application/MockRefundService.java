package com.beifanghui.backend.refund.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.refund.api.RefundResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MockRefundService {
    private static final DateTimeFormatter REFUND_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final JdbcTemplate jdbcTemplate;

    public MockRefundService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RefundResponse refund(AuthenticatedPrincipal principal, long orderId,
                                 String idempotencyKey, String reason) {
        validateIdempotencyKey(idempotencyKey);
        long userId = requireDatabaseUser(principal);
        String channelRefundId = "MOCKREF:" + orderId + ":" + idempotencyKey;
        RefundResponse duplicate = findByChannelRefundId(channelRefundId);
        if (duplicate != null) return duplicate;

        RefundOrder order = lockOwnedOrder(userId, orderId);
        if (!"PAID".equals(order.status()) && !"READY".equals(order.status())) {
            throw new BusinessException(CommonErrorCode.REFUND_CONFLICT,
                    "只有已支付且未使用的订单可以退款，当前状态为 " + order.status());
        }
        Integer usedTickets = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bf_verification v
                JOIN bf_order_item i ON i.id=v.order_item_id
                WHERE i.order_id=? AND v.status='USED'
                """, Integer.class, orderId);
        if (usedTickets != null && usedTickets > 0) {
            throw new BusinessException(CommonErrorCode.REFUND_CONFLICT, "订单中已有电子票核销，不能整单退款");
        }
        Payment payment = findSuccessfulPayment(orderId);
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "用户申请退款";
        if (normalizedReason.length() > 255) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "退款原因最长255个字符");
        }
        long refundId = insertRefund(order, payment, channelRefundId, normalizedReason, userId);
        restoreInventory(order);
        jdbcTemplate.update("""
                UPDATE bf_verification v JOIN bf_order_item i ON i.id=v.order_item_id
                SET v.status='VOID' WHERE i.order_id=? AND v.status='UNUSED'
                """, orderId);
        int changed = jdbcTemplate.update("""
                UPDATE bf_order SET status='REFUNDED'
                WHERE id=? AND user_id=? AND status IN ('PAID','READY')
                """, orderId, userId);
        if (changed != 1) throw new BusinessException(CommonErrorCode.REFUND_CONFLICT, "订单状态已变化，请刷新后重试");
        jdbcTemplate.update("""
                INSERT INTO bf_order_status_log
                (order_id, from_status, to_status, operator_id, reason, idempotency_key)
                VALUES (?, ?, 'REFUNDED', ?, ?, ?)
                """, orderId, order.status(), userId, normalizedReason, "REFUND:" + channelRefundId);
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log (operator_id, action, target_type, target_id, detail)
                VALUES (?, 'ORDER_MOCK_REFUND', 'ORDER', ?, JSON_OBJECT('refundNo', ?, 'amountCent', ?))
                """, userId, String.valueOf(orderId), refundNo(refundId), order.paidAmountCent());
        return findRefund(refundId);
    }

    private void restoreInventory(RefundOrder order) {
        List<RefundItem> items = jdbcTemplate.query("""
                SELECT i.id, i.sku_id, i.quantity, i.service_date, COALESCE(i.time_slot,'') time_slot,
                       inv.id inventory_id
                FROM bf_order_item i JOIN bf_inventory inv
                  ON inv.sku_id=i.sku_id AND inv.business_date=i.service_date
                 AND inv.time_slot=COALESCE(i.time_slot,'')
                WHERE i.order_id=? FOR UPDATE
                """, (rs, n) -> new RefundItem(rs.getLong("id"), rs.getInt("quantity"),
                rs.getLong("inventory_id")), order.id());
        for (RefundItem item : items) {
            String logKey = "REFUND-RELEASE:" + order.orderNo() + ":" + item.orderItemId();
            Integer logged = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bf_inventory_log WHERE idempotency_key=?", Integer.class, logKey);
            if (logged != null && logged > 0) continue;
            jdbcTemplate.update("""
                    UPDATE bf_inventory SET available_quantity=available_quantity+?, version=version+1 WHERE id=?
                    """, item.quantity(), item.inventoryId());
            Integer after = jdbcTemplate.queryForObject(
                    "SELECT available_quantity FROM bf_inventory WHERE id=?", Integer.class, item.inventoryId());
            jdbcTemplate.update("""
                    INSERT INTO bf_inventory_log
                    (inventory_id, order_no, change_type, quantity_delta, quantity_after, idempotency_key, remark)
                    VALUES (?, ?, 'REFUND_RELEASE', ?, ?, ?, '模拟退款归还库存')
                    """, item.inventoryId(), order.orderNo(), item.quantity(), after, logKey);
        }
    }

    private long insertRefund(RefundOrder order, Payment payment, String channelRefundId,
                              String reason, long userId) {
        String refundNo = "REF" + LocalDateTime.now().format(REFUND_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_refund
                    (refund_no, order_id, payment_id, channel_refund_id, amount_cent, reason,
                     status, requested_by, refunded_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', ?, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, refundNo);
            statement.setLong(2, order.id());
            statement.setLong(3, payment.id());
            statement.setString(4, channelRefundId);
            statement.setLong(5, order.paidAmountCent());
            statement.setString(6, reason);
            statement.setLong(7, userId);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private RefundOrder lockOwnedOrder(long userId, long orderId) {
        List<RefundOrder> rows = jdbcTemplate.query("""
                SELECT id, order_no, status, paid_amount_cent FROM bf_order
                WHERE id=? AND user_id=? FOR UPDATE
                """, (rs, n) -> new RefundOrder(rs.getLong("id"), rs.getString("order_no"),
                rs.getString("status"), rs.getLong("paid_amount_cent")), orderId, userId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在或无权访问");
        return rows.get(0);
    }

    private Payment findSuccessfulPayment(long orderId) {
        List<Payment> rows = jdbcTemplate.query("""
                SELECT id FROM bf_payment WHERE order_id=? AND status='SUCCESS' ORDER BY id LIMIT 1
                """, (rs, n) -> new Payment(rs.getLong("id")), orderId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.REFUND_CONFLICT, "未找到成功支付流水");
        return rows.get(0);
    }

    private RefundResponse findByChannelRefundId(String id) {
        List<RefundResponse> rows = jdbcTemplate.query(refundSelect() + " WHERE r.channel_refund_id=?",
                refundMapper(), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private RefundResponse findRefund(long id) {
        return jdbcTemplate.queryForObject(refundSelect() + " WHERE r.id=?", refundMapper(), id);
    }

    private String refundSelect() {
        return """
                SELECT r.id, r.order_id, o.order_no, r.refund_no, r.channel_refund_id,
                       r.amount_cent, r.reason, r.status, r.refunded_at
                FROM bf_refund r JOIN bf_order o ON o.id=r.order_id
                """;
    }

    private org.springframework.jdbc.core.RowMapper<RefundResponse> refundMapper() {
        return (rs, n) -> new RefundResponse(rs.getLong("id"), rs.getLong("order_id"),
                rs.getString("order_no"), rs.getString("refund_no"), rs.getString("channel_refund_id"),
                rs.getLong("amount_cent"), "CNY", rs.getString("reason"), rs.getString("status"),
                rs.getObject("refunded_at", LocalDateTime.class));
    }

    private long requireDatabaseUser(AuthenticatedPrincipal principal) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM bf_user WHERE wechat_openid=?", Long.class,
                "mock:" + principal.userId());
        if (ids.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "当前用户尚无业务数据");
        return ids.get(0);
    }

    private void validateIdempotencyKey(String key) {
        if (!StringUtils.hasText(key) || key.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "Idempotency-Key不能为空且最长64字符");
        }
    }

    private String refundNo(long refundId) {
        return jdbcTemplate.queryForObject("SELECT refund_no FROM bf_refund WHERE id=?", String.class, refundId);
    }

    private record RefundOrder(long id, String orderNo, String status, long paidAmountCent) {}
    private record RefundItem(long orderItemId, int quantity, long inventoryId) {}
    private record Payment(long id) {}
}
