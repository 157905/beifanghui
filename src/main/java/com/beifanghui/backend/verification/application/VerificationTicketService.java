package com.beifanghui.backend.verification.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.order.domain.OrderStatePolicy;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import com.beifanghui.backend.verification.api.VerificationTicketResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class VerificationTicketService {
    private final JdbcTemplate jdbcTemplate;
    private final byte[] signingKey;

    public VerificationTicketService(JdbcTemplate jdbcTemplate,
                                     @Value("${app.verification.signing-key:local-only-change-me}") String signingKey) {
        this.jdbcTemplate = jdbcTemplate;
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void issueOrderTickets(long orderId) {
        List<IssueItem> items = jdbcTemplate.query("""
                SELECT i.id, i.quantity, o.order_no
                FROM bf_order_item i JOIN bf_order o ON o.id = i.order_id
                WHERE o.id = ? AND o.status IN ('PAID', 'READY', 'COMPLETED')
                """, (rs, n) -> new IssueItem(rs.getLong("id"), rs.getInt("quantity"), rs.getString("order_no")), orderId);
        for (IssueItem item : items) {
            for (int ticketNo = 1; ticketNo <= item.quantity(); ticketNo++) {
                String code = ticketCode(item.orderNo(), item.orderItemId(), ticketNo);
                jdbcTemplate.update("""
                        INSERT IGNORE INTO bf_verification
                        (order_item_id, ticket_no, verification_code_hash, status)
                        VALUES (?, ?, ?, 'UNUSED')
                        """, item.orderItemId(), ticketNo, sha256(code));
            }
        }
    }

    @Transactional
    public List<VerificationTicketResponse> listOwnedTickets(AuthenticatedPrincipal principal, long orderId) {
        long userId = requireDatabaseUser(principal);
        Integer owned = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bf_order WHERE id = ? AND user_id = ?", Integer.class, orderId, userId);
        if (owned == null || owned == 0) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在或无权访问");
        }
        issueOrderTickets(orderId);
        return jdbcTemplate.query("""
                SELECT v.id, v.ticket_no, v.status, v.verified_at,
                       i.id order_item_id, i.resource_name, i.resource_type, i.service_date,
                       o.id order_id, o.order_no
                FROM bf_verification v
                JOIN bf_order_item i ON i.id = v.order_item_id
                JOIN bf_order o ON o.id = i.order_id
                WHERE o.id = ? AND o.user_id = ?
                ORDER BY i.id, v.ticket_no
                """, (rs, n) -> new VerificationTicketResponse(
                rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_no"),
                rs.getLong("order_item_id"), rs.getInt("ticket_no"), rs.getString("resource_name"),
                rs.getString("resource_type"), ticketCode(rs.getString("order_no"),
                rs.getLong("order_item_id"), rs.getInt("ticket_no")), rs.getString("status"),
                rs.getObject("service_date", LocalDate.class), rs.getObject("verified_at", LocalDateTime.class)),
                orderId, userId);
    }

    @Transactional
    public VerificationTicketResponse consume(AuthenticatedPrincipal operator, String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "核销码不能为空");
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        long operatorId = ensureDatabaseOperator(operator);
        List<LockedTicket> rows = jdbcTemplate.query("""
                SELECT v.id, v.ticket_no, v.status, i.id order_item_id,
                       i.resource_name, i.resource_type, i.service_date, o.id order_id, o.order_no, o.status order_status
                FROM bf_verification v
                JOIN bf_order_item i ON i.id = v.order_item_id
                JOIN bf_order o ON o.id = i.order_id
                WHERE v.verification_code_hash = ? FOR UPDATE
                """, (rs, n) -> new LockedTicket(rs.getLong("id"), rs.getInt("ticket_no"),
                rs.getString("status"), rs.getLong("order_item_id"), rs.getString("resource_name"),
                rs.getString("resource_type"), rs.getObject("service_date", LocalDate.class),
                rs.getLong("order_id"), rs.getString("order_no"), rs.getString("order_status")), sha256(normalizedCode));
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "核销码不存在");
        LockedTicket ticket = rows.get(0);
        if (!"UNUSED".equals(ticket.status())) {
            throw new BusinessException(CommonErrorCode.VERIFICATION_CONFLICT, "该电子票已核销或已失效，状态为 " + ticket.status());
        }
        if (!OrderStatePolicy.canVerify(ticket.orderStatus())) {
            throw new BusinessException(CommonErrorCode.VERIFICATION_CONFLICT, "订单状态不允许核销：" + ticket.orderStatus());
        }
        if (ticket.serviceDate() != null && !ticket.serviceDate().equals(LocalDate.now())) {
            throw new BusinessException(CommonErrorCode.VERIFICATION_CONFLICT, "电子票仅可在服务日期 " + ticket.serviceDate() + " 使用");
        }
        int changed = jdbcTemplate.update("""
                UPDATE bf_verification SET status='USED', verified_by=?, verified_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='UNUSED'
                """, operatorId, ticket.id());
        if (changed != 1) throw new BusinessException(CommonErrorCode.VERIFICATION_CONFLICT, "电子票已被其他工作人员核销");
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log (operator_id, action, target_type, target_id, detail)
                VALUES (?, 'VERIFICATION_CONSUME', 'VERIFICATION', ?, JSON_OBJECT('orderId', ?, 'channel', ?))
                """, operatorId, String.valueOf(ticket.id()), ticket.orderId(), operator.accountType());
        completeOrderWhenAllUsed(ticket.orderId(), operatorId);
        LocalDateTime verifiedAt = jdbcTemplate.queryForObject(
                "SELECT verified_at FROM bf_verification WHERE id=?", LocalDateTime.class, ticket.id());
        return new VerificationTicketResponse(ticket.id(), ticket.orderId(), ticket.orderNo(), ticket.orderItemId(),
                ticket.ticketNo(), ticket.resourceName(), ticket.resourceType(), null, "USED", ticket.serviceDate(), verifiedAt);
    }

    private void completeOrderWhenAllUsed(long orderId, long operatorId) {
        Integer unused = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bf_verification v JOIN bf_order_item i ON i.id=v.order_item_id
                WHERE i.order_id=? AND v.status='UNUSED'
                """, Integer.class, orderId);
        if (unused != null && unused == 0) {
            int changed = jdbcTemplate.update(
                    "UPDATE bf_order SET status='COMPLETED' WHERE id=? AND status IN ('PAID','READY')", orderId);
            if (changed == 1) {
                jdbcTemplate.update("""
                        INSERT INTO bf_order_status_log
                        (order_id, from_status, to_status, operator_id, reason, idempotency_key)
                        VALUES (?, 'PAID', 'COMPLETED', ?, '全部电子票核销完成', ?)
                        """, orderId, operatorId, "VERIFY-COMPLETE:" + orderId);
            }
        }
    }

    private long requireDatabaseUser(AuthenticatedPrincipal principal) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM bf_user WHERE wechat_openid=?", Long.class,
                principal.databaseOpenId());
        if (ids.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "当前用户尚无业务数据");
        return ids.get(0);
    }

    private long ensureDatabaseOperator(AuthenticatedPrincipal principal) {
        String openid = principal.databaseOpenId();
        jdbcTemplate.update("""
                INSERT INTO bf_user (wechat_openid, nickname, status)
                VALUES (?, ?, 'ACTIVE') ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), status='ACTIVE'
                """, openid, principal.displayName());
        return jdbcTemplate.queryForObject("SELECT id FROM bf_user WHERE wechat_openid=?", Long.class, openid);
    }

    private String ticketCode(String orderNo, long orderItemId, int ticketNo) {
        String payload = orderNo + ":" + orderItemId + ":" + ticketNo;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 12).toUpperCase(Locale.ROOT);
            return "BFHV-" + orderItemId + "-" + ticketNo + "-" + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成电子票", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算核销码摘要", ex);
        }
    }

    private record IssueItem(long orderItemId, int quantity, String orderNo) {}
    private record LockedTicket(long id, int ticketNo, String status, long orderItemId,
                                String resourceName, String resourceType, LocalDate serviceDate,
                                long orderId, String orderNo, String orderStatus) {}
}
