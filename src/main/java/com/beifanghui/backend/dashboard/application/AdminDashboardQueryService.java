package com.beifanghui.backend.dashboard.application;

import com.beifanghui.backend.dashboard.api.AdminDashboardSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class AdminDashboardQueryService {
    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminDashboardSummaryResponse summary() {
        return jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM bf_order
                    WHERE created_at>=CURRENT_DATE AND created_at<CURRENT_DATE + INTERVAL 1 DAY) today_order_count,
                  (SELECT COUNT(*) FROM bf_order WHERE status='PENDING_PAYMENT') pending_payment_order_count,
                  (SELECT COUNT(*) FROM bf_verification WHERE status='UNUSED') pending_verification_count,
                  (SELECT COUNT(*) FROM bf_resource WHERE status='ACTIVE') active_resource_count,
                  (SELECT COUNT(*) FROM bf_resource_sku WHERE status='ACTIVE') active_sku_count,
                  (SELECT COUNT(*) FROM bf_refund WHERE status='PENDING_REVIEW') pending_refund_count,
                  (SELECT COALESCE(SUM(amount_cent),0) FROM bf_payment
                    WHERE status='SUCCESS' AND paid_at>=CURRENT_DATE AND paid_at<CURRENT_DATE + INTERVAL 1 DAY) today_revenue_cent
                """, (rs, rowNum) -> new AdminDashboardSummaryResponse(
                rs.getLong("today_order_count"),
                rs.getLong("pending_payment_order_count"),
                rs.getLong("pending_verification_count"),
                rs.getLong("active_resource_count"),
                rs.getLong("active_sku_count"),
                rs.getLong("pending_refund_count"),
                rs.getLong("today_revenue_cent"),
                "CNY",
                LocalDateTime.now()));
    }
}
