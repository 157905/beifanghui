package com.beifanghui.backend.user.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import com.beifanghui.backend.user.api.AdminUserDetailResponse;
import com.beifanghui.backend.user.api.AdminUserStatusUpdateRequest;
import com.beifanghui.backend.user.api.AdminUserSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminUserService {

    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "DISABLED");
    private final JdbcTemplate jdbcTemplate;

    public AdminUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> list(
            String keyword, String status, String levelCode, int page, int pageSize) {
        validatePage(page, pageSize);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (u.nickname LIKE ? OR u.mobile LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
        }
        if (StringUtils.hasText(status)) {
            String value = normalizeStatus(status);
            where.append(" AND u.status=?");
            args.add(value);
        }
        if (StringUtils.hasText(levelCode)) {
            where.append(" AND COALESCE(m.level_code,'NORMAL')=?");
            args.add(levelCode.trim().toUpperCase(Locale.ROOT));
        }

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bf_user u LEFT JOIN bf_member_account m ON m.user_id=u.id" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<AdminUserSummaryResponse> items = jdbcTemplate.query("""
                SELECT u.id,u.nickname,u.mobile,u.status,COALESCE(m.level_code,'NORMAL') level_code,
                       COALESCE(m.points,0) points,COALESCE(m.growth_value,0) growth_value,
                       (SELECT COUNT(*) FROM bf_order o WHERE o.user_id=u.id) order_count,
                       GREATEST(
                         COALESCE((SELECT SUM(p.amount_cent) FROM bf_payment p JOIN bf_order po ON po.id=p.order_id
                                   WHERE po.user_id=u.id AND p.status='SUCCESS'),0)
                         - COALESCE((SELECT SUM(r.amount_cent) FROM bf_refund r JOIN bf_order ro ON ro.id=r.order_id
                                     WHERE ro.user_id=u.id AND r.status='SUCCESS'),0),0) cumulative_spend_cent,
                       u.last_login_at,u.created_at
                FROM bf_user u LEFT JOIN bf_member_account m ON m.user_id=u.id
                """ + where + " ORDER BY u.id DESC LIMIT ? OFFSET ?", (rs, rowNum) ->
                new AdminUserSummaryResponse(
                        rs.getLong("id"), rs.getString("nickname"), maskMobile(rs.getString("mobile")),
                        rs.getString("status"), rs.getString("level_code"), rs.getInt("points"),
                        rs.getInt("growth_value"), rs.getLong("order_count"), rs.getLong("cumulative_spend_cent"),
                        rs.getObject("last_login_at", LocalDateTime.class),
                        rs.getObject("created_at", LocalDateTime.class)), pageArgs.toArray());
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse detail(long userId) {
        List<UserHead> heads = jdbcTemplate.query("""
                SELECT u.id,u.nickname,u.real_name,u.mobile,u.avatar_url,u.gender,u.status,
                       COALESCE(m.level_code,'NORMAL') level_code,COALESCE(m.points,0) points,
                       COALESCE(m.balance_cent,0) balance_cent,COALESCE(m.growth_value,0) growth_value,
                       (SELECT COUNT(*) FROM bf_order o WHERE o.user_id=u.id) order_count,
                       GREATEST(
                         COALESCE((SELECT SUM(p.amount_cent) FROM bf_payment p JOIN bf_order po ON po.id=p.order_id
                                   WHERE po.user_id=u.id AND p.status='SUCCESS'),0)
                         - COALESCE((SELECT SUM(r.amount_cent) FROM bf_refund r JOIN bf_order ro ON ro.id=r.order_id
                                     WHERE ro.user_id=u.id AND r.status='SUCCESS'),0),0) cumulative_spend_cent,
                       (SELECT COUNT(*) FROM bf_refund r JOIN bf_order ro ON ro.id=r.order_id WHERE ro.user_id=u.id) refund_count,
                       (SELECT COUNT(*) FROM bf_verification v JOIN bf_order_item oi ON oi.id=v.order_item_id
                         JOIN bf_order vo ON vo.id=oi.order_id WHERE vo.user_id=u.id) verification_count,
                       u.last_login_at,u.created_at
                FROM bf_user u LEFT JOIN bf_member_account m ON m.user_id=u.id WHERE u.id=?
                """, (rs, rowNum) -> new UserHead(
                rs.getLong("id"), rs.getString("nickname"), rs.getString("real_name"),
                maskMobile(rs.getString("mobile")), rs.getString("avatar_url"), rs.getInt("gender"),
                rs.getString("status"), rs.getString("level_code"), rs.getInt("points"),
                rs.getLong("balance_cent"), rs.getInt("growth_value"), rs.getLong("order_count"),
                rs.getLong("cumulative_spend_cent"), rs.getLong("refund_count"),
                rs.getLong("verification_count"), rs.getObject("last_login_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)), userId);
        if (heads.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        UserHead head = heads.get(0);

        List<AdminUserDetailResponse.OrderItem> orders = jdbcTemplate.query("""
                SELECT id,order_no,status,payable_amount_cent,paid_amount_cent,created_at
                FROM bf_order WHERE user_id=? ORDER BY id DESC LIMIT 20
                """, (rs, rowNum) -> new AdminUserDetailResponse.OrderItem(
                rs.getLong("id"), rs.getString("order_no"), rs.getString("status"),
                rs.getLong("payable_amount_cent"), rs.getLong("paid_amount_cent"),
                rs.getObject("created_at", LocalDateTime.class)), userId);
        List<AdminUserDetailResponse.RefundItem> refunds = jdbcTemplate.query("""
                SELECT r.id,r.refund_no,r.order_id,r.status,r.amount_cent,r.reason,r.created_at
                FROM bf_refund r JOIN bf_order o ON o.id=r.order_id
                WHERE o.user_id=? ORDER BY r.id DESC LIMIT 20
                """, (rs, rowNum) -> new AdminUserDetailResponse.RefundItem(
                rs.getLong("id"), rs.getString("refund_no"), rs.getLong("order_id"),
                rs.getString("status"), rs.getLong("amount_cent"), rs.getString("reason"),
                rs.getObject("created_at", LocalDateTime.class)), userId);
        List<AdminUserDetailResponse.VerificationItem> verifications = jdbcTemplate.query("""
                SELECT v.id,o.id order_id,oi.resource_name,v.status,v.verified_at,v.created_at
                FROM bf_verification v JOIN bf_order_item oi ON oi.id=v.order_item_id
                JOIN bf_order o ON o.id=oi.order_id WHERE o.user_id=? ORDER BY v.id DESC LIMIT 20
                """, (rs, rowNum) -> new AdminUserDetailResponse.VerificationItem(
                rs.getLong("id"), rs.getLong("order_id"), rs.getString("resource_name"),
                rs.getString("status"), rs.getObject("verified_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)), userId);

        return new AdminUserDetailResponse(head.id(), head.nickname(), head.realName(), head.mobile(),
                head.avatarUrl(), head.gender(), head.status(), head.levelCode(), head.points(), head.balanceCent(),
                head.growthValue(), head.orderCount(), head.cumulativeSpendCent(), head.refundCount(),
                head.verificationCount(), head.lastLoginAt(), head.createdAt(), orders, refunds, verifications);
    }

    @Transactional
    public AdminUserDetailResponse updateStatus(
            AuthenticatedPrincipal principal, long userId, AdminUserStatusUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "请求内容不能为空");
        }
        String newStatus = normalizeStatus(request.status());
        String reason = normalizeReason(request.reason());
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM bf_user WHERE id=? FOR UPDATE", String.class, userId);
        if (statuses.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        long operatorId = ensureOperator(principal);
        if (operatorId == userId && "DISABLED".equals(newStatus)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "不能禁用当前登录账号");
        }
        String oldStatus = statuses.get(0);
        if (!oldStatus.equals(newStatus)) {
            jdbcTemplate.update("UPDATE bf_user SET status=? WHERE id=?", newStatus, userId);
            jdbcTemplate.update("""
                    INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail)
                    VALUES (?,'USER_STATUS_UPDATE','USER',?,JSON_OBJECT('fromStatus',?,'toStatus',?,'reason',?))
                    """, operatorId, String.valueOf(userId), oldStatus, newStatus, reason);
        }
        return detail(userId);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page最小为1，pageSize范围为1—100");
        }
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "status不能为空");
        }
        String result = status.trim().toUpperCase(Locale.ROOT);
        if (!USER_STATUSES.contains(result)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "status仅支持ACTIVE或DISABLED");
        }
        return result;
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "状态变更原因不能为空");
        }
        String result = reason.trim();
        if (result.length() > 255) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "状态变更原因最长255字符");
        }
        return result;
    }

    private long ensureOperator(AuthenticatedPrincipal principal) {
        String openId = principal.databaseOpenId();
        jdbcTemplate.update("""
                INSERT INTO bf_user(wechat_openid,nickname,status) VALUES (?,?,'ACTIVE')
                ON DUPLICATE KEY UPDATE nickname=VALUES(nickname)
                """, openId, principal.displayName());
        return jdbcTemplate.queryForObject("SELECT id FROM bf_user WHERE wechat_openid=?", Long.class, openId);
    }

    private static String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile) || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private record UserHead(long id, String nickname, String realName, String mobile, String avatarUrl,
                            int gender, String status, String levelCode, int points, long balanceCent,
                            int growthValue, long orderCount, long cumulativeSpendCent, long refundCount,
                            long verificationCount, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    }
}
