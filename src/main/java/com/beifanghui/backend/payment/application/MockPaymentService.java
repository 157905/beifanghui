package com.beifanghui.backend.payment.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.order.domain.OrderStatePolicy;
import com.beifanghui.backend.payment.api.PaymentResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import com.beifanghui.backend.verification.application.VerificationTicketService;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MockPaymentService {
    private static final DateTimeFormatter PAYMENT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final JdbcTemplate jdbcTemplate;
    private final VerificationTicketService verificationTicketService;
    private final PaymentGateway paymentGateway;

    public MockPaymentService(
            JdbcTemplate jdbcTemplate,
            VerificationTicketService verificationTicketService,
            PaymentGateway paymentGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.verificationTicketService = verificationTicketService;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public PaymentResponse pay(AuthenticatedPrincipal principal, long orderId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        long userId = requireDatabaseUser(principal);
        PaymentOrder order = lockOwnedOrder(userId, orderId);
        PaymentResponse completed = findSuccessfulPayment(orderId);
        if (completed != null) {
            verificationTicketService.issueOrderTickets(orderId);
            return completed;
        }
        if (!OrderStatePolicy.canPay(order.status())) {
            throw new BusinessException(CommonErrorCode.PAYMENT_CONFLICT,
                    "只有待支付订单可以支付，当前状态为 " + order.status());
        }
        if (order.expiresAt() != null && order.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(CommonErrorCode.PAYMENT_CONFLICT, "订单已超过支付截止时间");
        }
        PaymentGateway.PaymentExecution execution = paymentGateway.execute(
                new PaymentGateway.PaymentCommand(orderId, order.payableAmountCent(), "CNY", idempotencyKey));
        PaymentResponse duplicate = findByTransactionId(execution.transactionId());
        if (duplicate != null) return duplicate;

        String paymentNo = nextPaymentNo();
        long paymentId = insertPayment(order, paymentNo, execution);
        int changed = jdbcTemplate.update("""
                UPDATE bf_order SET status = 'PAID', paid_amount_cent = payable_amount_cent
                WHERE id = ? AND user_id = ? AND status = 'PENDING_PAYMENT'
                """, orderId, userId);
        if (changed != 1) {
            throw new BusinessException(CommonErrorCode.PAYMENT_CONFLICT, "订单状态已变化，请刷新后重试");
        }
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log (operator_id, action, target_type, target_id, detail)
                VALUES (?, 'ORDER_MOCK_PAY', 'ORDER', ?, JSON_OBJECT('paymentNo', ?, 'amountCent', ?))
                """, userId, String.valueOf(orderId), paymentNo, order.payableAmountCent());
        verificationTicketService.issueOrderTickets(orderId);
        return findPayment(paymentId);
    }

    private long insertPayment(
            PaymentOrder order,
            String paymentNo,
            PaymentGateway.PaymentExecution execution) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_payment
                    (order_id, payment_no, channel, transaction_id, amount_cent, status, paid_at, callback_payload)
                    VALUES (?, ?, ?, ?, ?, 'SUCCESS', CURRENT_TIMESTAMP,
                            JSON_OBJECT('source', ?, 'verified', ?))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, order.id());
            statement.setString(2, paymentNo);
            statement.setString(3, execution.channel());
            statement.setString(4, execution.transactionId());
            statement.setLong(5, order.payableAmountCent());
            statement.setString(6, execution.source());
            statement.setBoolean(7, execution.verified());
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private PaymentOrder lockOwnedOrder(long userId, long orderId) {
        List<PaymentOrder> rows = jdbcTemplate.query("""
                SELECT id, status, payable_amount_cent, expires_at
                FROM bf_order WHERE id = ? AND user_id = ? FOR UPDATE
                """, (rs, n) -> new PaymentOrder(rs.getLong("id"), rs.getString("status"),
                rs.getLong("payable_amount_cent"), rs.getObject("expires_at", LocalDateTime.class)), orderId, userId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "订单不存在或无权访问");
        return rows.get(0);
    }

    private PaymentResponse findSuccessfulPayment(long orderId) {
        List<PaymentResponse> rows = jdbcTemplate.query("""
                SELECT p.id, p.order_id, o.order_no, p.payment_no, p.channel, p.transaction_id,
                       p.amount_cent, p.status, p.paid_at
                FROM bf_payment p JOIN bf_order o ON o.id = p.order_id
                WHERE p.order_id = ? AND p.status = 'SUCCESS' ORDER BY p.id LIMIT 1
                """, paymentMapper(), orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private PaymentResponse findByTransactionId(String transactionId) {
        List<PaymentResponse> rows = jdbcTemplate.query("""
                SELECT p.id, p.order_id, o.order_no, p.payment_no, p.channel, p.transaction_id,
                       p.amount_cent, p.status, p.paid_at
                FROM bf_payment p JOIN bf_order o ON o.id = p.order_id WHERE p.transaction_id = ?
                """, paymentMapper(), transactionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private PaymentResponse findPayment(long paymentId) {
        return jdbcTemplate.queryForObject("""
                SELECT p.id, p.order_id, o.order_no, p.payment_no, p.channel, p.transaction_id,
                       p.amount_cent, p.status, p.paid_at
                FROM bf_payment p JOIN bf_order o ON o.id = p.order_id WHERE p.id = ?
                """, paymentMapper(), paymentId);
    }

    private RowMapper<PaymentResponse> paymentMapper() {
        return (rs, n) -> new PaymentResponse(rs.getLong("id"), rs.getLong("order_id"),
                rs.getString("order_no"), rs.getString("payment_no"), rs.getString("channel"),
                rs.getString("transaction_id"), rs.getLong("amount_cent"), "CNY",
                rs.getString("status"), rs.getObject("paid_at", LocalDateTime.class));
    }

    private long requireDatabaseUser(AuthenticatedPrincipal principal) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM bf_user WHERE wechat_openid = ?",
                Long.class, "mock:" + principal.userId());
        if (ids.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "当前用户尚未创建订单记录");
        return ids.get(0);
    }

    private void validateIdempotencyKey(String key) {
        if (!StringUtils.hasText(key) || key.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "Idempotency-Key 不能为空且最长64字符");
        }
    }

    private String nextPaymentNo() {
        return "PAY" + LocalDateTime.now().format(PAYMENT_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private record PaymentOrder(long id, String status, long payableAmountCent, LocalDateTime expiresAt) {}
}
