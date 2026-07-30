package com.beifanghui.backend.order.application;

import com.beifanghui.backend.order.api.AdminOrderSummaryResponse;
import com.beifanghui.backend.order.api.OrderResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminOrderQueryService {
    private final JdbcTemplate jdbcTemplate;
    public AdminOrderQueryService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public PageResponse<AdminOrderSummaryResponse> list(String status, String orderNo, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page最小为1，pageSize范围为1—100");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(status)) { where.append(" AND o.status=?"); args.add(status.trim().toUpperCase()); }
        if (StringUtils.hasText(orderNo)) { where.append(" AND o.order_no LIKE ?"); args.add("%" + orderNo.trim() + "%"); }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_order o" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<AdminOrderSummaryResponse> items = jdbcTemplate.query("""
                SELECT o.id,o.order_no,o.user_id,u.nickname,o.order_type,o.status,o.payable_amount_cent,
                       o.paid_amount_cent,o.expires_at,o.created_at
                FROM bf_order o JOIN bf_user u ON u.id=o.user_id
                """ + where + " ORDER BY o.id DESC LIMIT ? OFFSET ?", (rs, n) ->
                new AdminOrderSummaryResponse(rs.getLong("id"), rs.getString("order_no"), rs.getLong("user_id"),
                        rs.getString("nickname"), rs.getString("order_type"), rs.getString("status"),
                        rs.getLong("payable_amount_cent"), rs.getLong("paid_amount_cent"), "CNY",
                        rs.getObject("expires_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class)),
                pageArgs.toArray());
        int pages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, total, pages);
    }

    public OrderResponse detail(long orderId) {
        List<OrderResponse> heads = jdbcTemplate.query("""
                SELECT id,order_no,order_type,status,total_amount_cent,payable_amount_cent,paid_amount_cent,
                       expires_at,created_at,cancelled_at,remark FROM bf_order WHERE id=?
                """, (rs, n) -> new OrderResponse(rs.getLong("id"), rs.getString("order_no"),
                rs.getString("order_type"), rs.getString("status"), rs.getLong("total_amount_cent"),
                rs.getLong("payable_amount_cent"), rs.getLong("paid_amount_cent"), "CNY",
                rs.getObject("expires_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("cancelled_at", LocalDateTime.class), rs.getString("remark"), List.of()), orderId);
        if (heads.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在");
        OrderResponse h = heads.get(0);
        List<OrderResponse.Item> items = jdbcTemplate.query("""
                SELECT id,sku_id,sku_code,sku_name,resource_name,resource_type,quantity,unit_price_cent,
                       amount_cent,service_date,COALESCE(time_slot,'') time_slot
                FROM bf_order_item WHERE order_id=? ORDER BY id
                """, (rs, n) -> new OrderResponse.Item(rs.getLong("id"), rs.getLong("sku_id"),
                rs.getString("sku_code"), rs.getString("sku_name"), rs.getString("resource_name"),
                rs.getString("resource_type"), rs.getInt("quantity"), rs.getLong("unit_price_cent"),
                rs.getLong("amount_cent"), rs.getDate("service_date").toLocalDate(), rs.getString("time_slot")), orderId);
        return new OrderResponse(h.id(),h.orderNo(),h.orderType(),h.status(),h.totalAmountCent(),
                h.payableAmountCent(),h.paidAmountCent(),h.currency(),h.expiresAt(),h.createdAt(),h.cancelledAt(),h.remark(),items);
    }
}
