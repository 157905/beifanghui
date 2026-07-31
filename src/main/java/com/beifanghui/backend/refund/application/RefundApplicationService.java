package com.beifanghui.backend.refund.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.order.domain.OrderStatePolicy;
import com.beifanghui.backend.refund.api.RefundApplicationResponse;
import com.beifanghui.backend.refund.api.MockRefundCallbackRequest;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RefundApplicationService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final JdbcTemplate jdbcTemplate;
    private final String mockCallbackKey;
    public RefundApplicationService(JdbcTemplate jdbcTemplate,
            @Value("${app.refund.mock-callback-key:local-only-refund-callback}") String mockCallbackKey) {
        this.jdbcTemplate = jdbcTemplate;
        this.mockCallbackKey = mockCallbackKey;
    }

    @Transactional
    public RefundApplicationResponse apply(AuthenticatedPrincipal principal, long orderId, String key, String reason) {
        validatePage(1, 1);
        if (!StringUtils.hasText(key) || key.length() > 64) throw invalid("Idempotency-Key不能为空且最长64字符");
        long userId = requireUser(principal);
        List<RefundApplicationResponse> duplicate = jdbcTemplate.query(select() + " WHERE r.requested_by=? AND r.idempotency_key=?", mapper(), userId, key);
        if (!duplicate.isEmpty()) {
            if (duplicate.get(0).orderId() != orderId) throw conflict("该幂等键已用于另一订单");
            return duplicate.get(0);
        }
        Order order = lockOrder(orderId, userId);
        duplicate = jdbcTemplate.query(select() + " WHERE r.requested_by=? AND r.idempotency_key=?", mapper(), userId, key);
        if (!duplicate.isEmpty()) {
            if (duplicate.get(0).orderId() != orderId) throw conflict("该幂等键已用于另一订单");
            return duplicate.get(0);
        }
        validateRefundable(order);
        Integer active = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_refund WHERE order_id=? AND status IN ('PENDING_REVIEW','PROCESSING','SUCCESS')", Integer.class, orderId);
        if (active != null && active > 0) throw conflict("该订单已有处理中或成功的退款申请");
        long paymentId = requirePayment(orderId);
        String normalizedReason = text(reason, 255, "退款原因");
        KeyHolder keys = new GeneratedKeyHolder();
        String refundNo = "REF" + LocalDateTime.now().format(TIME) + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO bf_refund(refund_no,order_id,payment_id,amount_cent,reason,status,requested_by,idempotency_key)
                    VALUES (?,?,?,?,?,'PENDING_REVIEW',?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, refundNo); ps.setLong(2, order.id()); ps.setLong(3, paymentId);
            ps.setLong(4, order.paidAmountCent()); ps.setString(5, normalizedReason);
            ps.setLong(6, userId); ps.setString(7, key); return ps;
        }, keys);
        long id = keys.getKey().longValue();
        statusLog(id, null, "PENDING_REVIEW", userId, normalizedReason, "REFUND-APPLY:" + id);
        audit(userId, "REFUND_APPLY", id, "PENDING_REVIEW");
        return find(id);
    }

    @Transactional
    public RefundApplicationResponse approve(AuthenticatedPrincipal principal, long refundId, String comment) {
        long reviewerId = ensureUser(principal);
        RefundLocked refund = lockRefund(refundId);
        if ("SUCCESS".equals(refund.status())) return find(refundId);
        if (!"PENDING_REVIEW".equals(refund.status())) throw conflict("只有待审核退款申请可以通过");
        Order order = lockOrder(refund.orderId(), null);
        validateRefundable(order);
        String channelId = "MOCKREF:" + refund.refundNo();
        jdbcTemplate.update("UPDATE bf_refund SET status='PROCESSING',reviewed_by=?,review_comment=?,reviewed_at=CURRENT_TIMESTAMP,channel_refund_id=?,previous_order_status=? WHERE id=?",
                reviewerId, optionalText(comment, 500), channelId, order.status(), refundId);
        int changed = jdbcTemplate.update("UPDATE bf_order SET status='REFUNDING' WHERE id=? AND status IN ('PAID','READY')", order.id());
        if (changed != 1) throw conflict("订单状态已变化，请刷新后重试");
        jdbcTemplate.update("INSERT INTO bf_order_status_log(order_id,from_status,to_status,operator_id,reason,idempotency_key) VALUES (?,?,'REFUNDING',?,?,?)",
                order.id(), order.status(), reviewerId, "退款审核通过，等待渠道处理", "REFUND-PROCESSING:" + refundId);
        statusLog(refundId, "PENDING_REVIEW", "PROCESSING", reviewerId, optionalText(comment, 500), "REFUND-APPROVE:" + refundId);
        audit(reviewerId, "REFUND_APPROVE", refundId, "PROCESSING");
        return find(refundId);
    }

    @Transactional
    public RefundApplicationResponse callback(String callbackKey, MockRefundCallbackRequest request) {
        if (!StringUtils.hasText(callbackKey) || !mockCallbackKey.equals(callbackKey)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "退款回调密钥无效");
        }
        if (request == null || !StringUtils.hasText(request.channelRefundId()) || !StringUtils.hasText(request.status())) {
            throw invalid("退款回调参数不完整");
        }
        List<RefundLocked> rows = jdbcTemplate.query("SELECT id,order_id,refund_no,status,requested_by,reviewed_by,previous_order_status FROM bf_refund WHERE channel_refund_id=? FOR UPDATE",
                refundLockedMapper(), request.channelRefundId().trim());
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "退款流水不存在");
        RefundLocked refund = rows.get(0);
        if ("SUCCESS".equals(refund.status()) || "FAILED".equals(refund.status())) return find(refund.id());
        if (!"PROCESSING".equals(refund.status())) throw conflict("退款申请当前不等待渠道回调");
        if ("SUCCESS".equalsIgnoreCase(request.status())) {
            completeRefund(refund.id(), refund.reviewedBy() == null ? refund.requestedBy() : refund.reviewedBy(),
                    lockOrder(refund.orderId(), null));
        } else {
            jdbcTemplate.update("UPDATE bf_refund SET status='FAILED',failure_reason=? WHERE id=? AND status='PROCESSING'",
                    optionalText(request.message(), 500), refund.id());
            jdbcTemplate.update("UPDATE bf_order SET status=? WHERE id=? AND status='REFUNDING'",
                    refund.previousOrderStatus(), refund.orderId());
            statusLog(refund.id(), "PROCESSING", "FAILED", refund.reviewedBy(), optionalText(request.message(), 500), "REFUND-CALLBACK-FAILED:" + refund.id());
        }
        return find(refund.id());
    }

    @Transactional
    public RefundApplicationResponse reject(AuthenticatedPrincipal principal, long refundId, String comment) {
        String reason = text(comment, 500, "拒绝原因");
        long reviewerId = ensureUser(principal);
        RefundLocked refund = lockRefund(refundId);
        if (!"PENDING_REVIEW".equals(refund.status())) throw conflict("只有待审核退款申请可以拒绝");
        jdbcTemplate.update("UPDATE bf_refund SET status='REJECTED',reviewed_by=?,review_comment=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",
                reviewerId, reason, refundId);
        statusLog(refundId, "PENDING_REVIEW", "REJECTED", reviewerId, reason, "REFUND-REJECT:" + refundId);
        audit(reviewerId, "REFUND_REJECT", refundId, "REJECTED");
        return find(refundId);
    }

    @Transactional(readOnly=true)
    public PageResponse<RefundApplicationResponse> listMine(AuthenticatedPrincipal principal, int page, int pageSize) {
        return list(" WHERE r.requested_by=?", List.of(requireUser(principal)), page, pageSize);
    }

    @Transactional(readOnly=true)
    public PageResponse<RefundApplicationResponse> listAdmin(String status, int page, int pageSize) {
        List<Object> args = new ArrayList<>();
        String where = "";
        if (StringUtils.hasText(status)) { where = " WHERE r.status=?"; args.add(status.trim().toUpperCase(Locale.ROOT)); }
        return list(where, args, page, pageSize);
    }

    @Transactional(readOnly=true)
    public RefundApplicationResponse detailMine(AuthenticatedPrincipal principal, long refundId) {
        long userId = requireUser(principal);
        List<RefundApplicationResponse> rows = jdbcTemplate.query(select() + " WHERE r.id=? AND r.requested_by=?", mapper(), refundId, userId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "退款申请不存在或无权访问");
        return rows.get(0);
    }

    @Transactional(readOnly=true)
    public RefundApplicationResponse detailAdmin(long refundId) { return find(refundId); }

    private PageResponse<RefundApplicationResponse> list(String where, List<Object> args, int page, int pageSize) {
        validatePage(page, pageSize);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_refund r" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(pageSize); pageArgs.add((page - 1) * pageSize);
        List<RefundApplicationResponse> items = jdbcTemplate.query(select() + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?", mapper(), pageArgs.toArray());
        long count = total == null ? 0 : total;
        return new PageResponse<>(items, page, pageSize, count, count == 0 ? 0 : (int)((count + pageSize - 1) / pageSize));
    }

    private void validateRefundable(Order order) {
        if (!"SCENIC_TICKET".equals(order.orderType())) throw conflict("当前退款流程仅支持景区门票订单");
        if (!OrderStatePolicy.canRefund(order.status())) throw conflict("只有已支付且未使用的订单可以申请退款，当前状态为 " + order.status());
        Integer used = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_verification v JOIN bf_order_item i ON i.id=v.order_item_id WHERE i.order_id=? AND v.status='USED'", Integer.class, order.id());
        if (used != null && used > 0) throw conflict("订单中已有电子票核销，不能整单退款");
    }

    private void restoreInventory(Order order) {
        List<Item> items = jdbcTemplate.query("SELECT i.id,i.quantity,inv.id inventory_id FROM bf_order_item i JOIN bf_inventory inv ON inv.sku_id=i.sku_id AND inv.business_date=i.service_date AND inv.time_slot=COALESCE(i.time_slot,'') WHERE i.order_id=? FOR UPDATE",
                (rs,n)->new Item(rs.getLong("id"),rs.getInt("quantity"),rs.getLong("inventory_id")), order.id());
        Integer orderItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_order_item WHERE order_id=?", Integer.class, order.id());
        if (orderItemCount == null || items.size() != orderItemCount) throw conflict("订单库存记录不完整，已停止退款，请联系管理员");
        for (Item item : items) {
            String key = "REFUND-RELEASE:" + order.orderNo() + ":" + item.orderItemId();
            Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_inventory_log WHERE idempotency_key=?", Integer.class, key);
            if (exists != null && exists > 0) continue;
            jdbcTemplate.update("UPDATE bf_inventory SET available_quantity=available_quantity+?,version=version+1 WHERE id=?", item.quantity(), item.inventoryId());
            Integer after = jdbcTemplate.queryForObject("SELECT available_quantity FROM bf_inventory WHERE id=?", Integer.class, item.inventoryId());
            jdbcTemplate.update("INSERT INTO bf_inventory_log(inventory_id,order_no,change_type,quantity_delta,quantity_after,idempotency_key,remark) VALUES (?,?,'REFUND_RELEASE',?,?,?,'审核退款归还库存')",
                    item.inventoryId(), order.orderNo(), item.quantity(), after, key);
        }
    }

    private void completeRefund(long refundId, long operatorId, Order order) {
        restoreInventory(order);
        jdbcTemplate.update("UPDATE bf_verification v JOIN bf_order_item i ON i.id=v.order_item_id SET v.status='VOID' WHERE i.order_id=? AND v.status='UNUSED'", order.id());
        int changed = jdbcTemplate.update("UPDATE bf_order SET status='REFUNDED' WHERE id=? AND status='REFUNDING'", order.id());
        if (changed != 1) throw conflict("订单状态已变化，请刷新后重试");
        jdbcTemplate.update("UPDATE bf_refund SET status='SUCCESS',refunded_at=CURRENT_TIMESTAMP,failure_reason=NULL WHERE id=? AND status='PROCESSING'", refundId);
        jdbcTemplate.update("INSERT INTO bf_order_status_log(order_id,from_status,to_status,operator_id,reason,idempotency_key) VALUES (?,?,'REFUNDED',?,?,?)",
                order.id(), order.status(), operatorId, "退款执行成功", "REFUND-COMPLETE:" + refundId);
        statusLog(refundId, "PROCESSING", "SUCCESS", operatorId, "退款渠道处理成功", "REFUND-CALLBACK-SUCCESS:" + refundId);
        audit(operatorId, "REFUND_COMPLETE", refundId, "SUCCESS");
    }

    private Order lockOrder(long id, Long userId) {
        String sql = "SELECT id,order_no,order_type,status,paid_amount_cent FROM bf_order WHERE id=?" + (userId == null ? "" : " AND user_id=?") + " FOR UPDATE";
        List<Order> rows = jdbcTemplate.query(sql,(rs,n)->new Order(rs.getLong("id"),rs.getString("order_no"),rs.getString("order_type"),rs.getString("status"),rs.getLong("paid_amount_cent")),
                userId == null ? new Object[]{id} : new Object[]{id,userId});
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND,"订单不存在或无权访问");
        return rows.get(0);
    }

    private RefundLocked lockRefund(long id) {
        List<RefundLocked> rows=jdbcTemplate.query("SELECT id,order_id,refund_no,status,requested_by,reviewed_by,previous_order_status FROM bf_refund WHERE id=? FOR UPDATE",refundLockedMapper(),id);
        if(rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND,"退款申请不存在"); return rows.get(0);
    }
    private org.springframework.jdbc.core.RowMapper<RefundLocked> refundLockedMapper(){return(rs,n)->new RefundLocked(rs.getLong("id"),rs.getLong("order_id"),rs.getString("refund_no"),rs.getString("status"),rs.getLong("requested_by"),rs.getObject("reviewed_by",Long.class),rs.getString("previous_order_status"));}

    private long requirePayment(long orderId) {
        List<Long> ids=jdbcTemplate.queryForList("SELECT id FROM bf_payment WHERE order_id=? AND status='SUCCESS' ORDER BY id LIMIT 1",Long.class,orderId);
        if(ids.isEmpty()) throw conflict("未找到成功支付流水"); return ids.get(0);
    }

    private long requireUser(AuthenticatedPrincipal p) { List<Long> ids=jdbcTemplate.queryForList("SELECT id FROM bf_user WHERE wechat_openid=?",Long.class,p.databaseOpenId()); if(ids.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND,"当前用户尚无业务数据"); return ids.get(0); }
    private long ensureUser(AuthenticatedPrincipal p) { jdbcTemplate.update("INSERT INTO bf_user(wechat_openid,nickname,status) VALUES (?,?,'ACTIVE') ON DUPLICATE KEY UPDATE nickname=VALUES(nickname)",p.databaseOpenId(),p.displayName()); return requireUser(p); }
    private String text(String v,int max,String field){if(!StringUtils.hasText(v)||v.trim().length()>max)throw invalid(field+"不能为空且最长"+max+"个字符");return v.trim();}
    private String optionalText(String v,int max){if(!StringUtils.hasText(v))return null;if(v.trim().length()>max)throw invalid("审核备注最长"+max+"个字符");return v.trim();}
    private void validatePage(int p,int s){if(p<1||s<1||s>100)throw invalid("page最小为1，pageSize范围为1—100");}
    private BusinessException invalid(String m){return new BusinessException(CommonErrorCode.INVALID_REQUEST,m);} private BusinessException conflict(String m){return new BusinessException(CommonErrorCode.REFUND_CONFLICT,m);}
    private void audit(long user,String action,long id,String status){jdbcTemplate.update("INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail) VALUES (?,?,'REFUND',?,JSON_OBJECT('status',?))",user,action,String.valueOf(id),status);}
    private void statusLog(long refundId,String from,String to,Long operator,String note,String key){jdbcTemplate.update("INSERT IGNORE INTO bf_refund_status_log(refund_id,from_status,to_status,operator_id,note,idempotency_key) VALUES (?,?,?,?,?,?)",refundId,from,to,operator,note,key);}
    private RefundApplicationResponse find(long id){return jdbcTemplate.queryForObject(select()+" WHERE r.id=?",mapper(),id);}
    private String select(){return "SELECT r.id,r.order_id,o.order_no,r.refund_no,r.channel_refund_id,r.amount_cent,r.reason,r.status,r.requested_by,r.reviewed_by,u.nickname reviewer_name,r.review_comment,r.failure_reason,r.created_at,r.reviewed_at,r.refunded_at FROM bf_refund r JOIN bf_order o ON o.id=r.order_id LEFT JOIN bf_user u ON u.id=r.reviewed_by";}
    private org.springframework.jdbc.core.RowMapper<RefundApplicationResponse> mapper(){return(rs,n)->new RefundApplicationResponse(rs.getLong("id"),rs.getLong("order_id"),rs.getString("order_no"),rs.getString("refund_no"),rs.getString("channel_refund_id"),rs.getLong("amount_cent"),"CNY",rs.getString("reason"),rs.getString("status"),rs.getObject("requested_by",Long.class),rs.getObject("reviewed_by",Long.class),rs.getString("reviewer_name"),rs.getString("review_comment"),rs.getString("failure_reason"),rs.getObject("created_at",LocalDateTime.class),rs.getObject("reviewed_at",LocalDateTime.class),rs.getObject("refunded_at",LocalDateTime.class));}
    private record Order(long id,String orderNo,String orderType,String status,long paidAmountCent){} private record Item(long orderItemId,int quantity,long inventoryId){} private record RefundLocked(long id,long orderId,String refundNo,String status,long requestedBy,Long reviewedBy,String previousOrderStatus){}
}
