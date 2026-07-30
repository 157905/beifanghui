package com.beifanghui.backend.order.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExpiredOrderService {
    private final JdbcTemplate jdbcTemplate;

    public ExpiredOrderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${app.order.expiration-scan-ms:60000}", initialDelay = 30000)
    public void scheduledExpire() {
        expireOverdue();
    }

    @Transactional
    public int expireOverdue() {
        List<Long> orderIds = jdbcTemplate.queryForList("""
                SELECT id FROM bf_order
                WHERE status='PENDING_PAYMENT' AND expires_at IS NOT NULL AND expires_at < ?
                ORDER BY expires_at LIMIT 50
                """, Long.class, LocalDateTime.now());
        int expired = 0;
        for (Long orderId : orderIds) {
            if (expireOne(orderId)) expired++;
        }
        return expired;
    }

    private boolean expireOne(long orderId) {
        List<ExpiredOrder> orders = jdbcTemplate.query("""
                SELECT id, order_no, status, expires_at FROM bf_order WHERE id=? FOR UPDATE
                """, (rs, n) -> new ExpiredOrder(rs.getLong("id"), rs.getString("order_no"),
                rs.getString("status"), rs.getObject("expires_at", LocalDateTime.class)), orderId);
        if (orders.isEmpty()) return false;
        ExpiredOrder order = orders.get(0);
        if (!"PENDING_PAYMENT".equals(order.status()) || order.expiresAt() == null
                || !order.expiresAt().isBefore(LocalDateTime.now())) return false;

        List<ExpiredItem> items = jdbcTemplate.query("""
                SELECT oi.id, oi.quantity, inv.id inventory_id
                FROM bf_order_item oi JOIN bf_inventory inv
                  ON inv.sku_id=oi.sku_id AND inv.business_date=oi.service_date
                 AND inv.time_slot=COALESCE(oi.time_slot,'')
                WHERE oi.order_id=? ORDER BY inv.id FOR UPDATE
                """, (rs, n) -> new ExpiredItem(rs.getLong("id"), rs.getInt("quantity"),
                rs.getLong("inventory_id")), orderId);
        for (ExpiredItem item : items) {
            String key = "EXPIRE-RELEASE:" + order.orderNo() + ":" + item.orderItemId();
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bf_inventory_log WHERE idempotency_key=?", Integer.class, key);
            if (exists != null && exists > 0) continue;
            jdbcTemplate.update("""
                    UPDATE bf_inventory SET available_quantity=available_quantity+?, version=version+1 WHERE id=?
                    """, item.quantity(), item.inventoryId());
            Integer after = jdbcTemplate.queryForObject(
                    "SELECT available_quantity FROM bf_inventory WHERE id=?", Integer.class, item.inventoryId());
            jdbcTemplate.update("""
                    INSERT INTO bf_inventory_log
                    (inventory_id, order_no, change_type, quantity_delta, quantity_after, idempotency_key, remark)
                    VALUES (?, ?, 'EXPIRE_RELEASE', ?, ?, ?, '支付超时自动释放库存')
                    """, item.inventoryId(), order.orderNo(), item.quantity(), after, key);
        }
        int changed = jdbcTemplate.update("""
                UPDATE bf_order SET status='CANCELLED', cancelled_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='PENDING_PAYMENT'
                """, orderId);
        if (changed != 1) return false;
        jdbcTemplate.update("""
                INSERT IGNORE INTO bf_order_status_log
                (order_id, from_status, to_status, operator_id, reason, idempotency_key)
                VALUES (?, 'PENDING_PAYMENT', 'CANCELLED', NULL, '支付超时自动取消', ?)
                """, orderId, "EXPIRE:" + order.orderNo());
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log (operator_id, action, target_type, target_id, detail)
                VALUES (NULL, 'ORDER_EXPIRED', 'ORDER', ?, JSON_OBJECT('orderNo', ?))
                """, String.valueOf(orderId), order.orderNo());
        return true;
    }

    private record ExpiredOrder(long id, String orderNo, String status, LocalDateTime expiresAt) {}
    private record ExpiredItem(long orderItemId, int quantity, long inventoryId) {}
}
