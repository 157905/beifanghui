package com.beifanghui.backend.report.application;

import com.beifanghui.backend.report.api.DailyReportItem;
import com.beifanghui.backend.report.api.ReportOverviewResponse;
import com.beifanghui.backend.report.api.ResourceSalesReportItem;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminReportService {

    private static final int MAX_RANGE_DAYS = 366;
    private final JdbcTemplate jdbcTemplate;

    public AdminReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReportOverviewResponse overview(LocalDate requestedStart, LocalDate requestedEnd) {
        Period period = period(requestedStart, requestedEnd);
        LocalDateTime start = period.start().atStartOfDay();
        LocalDateTime endExclusive = period.end().plusDays(1).atStartOfDay();

        long orderCount = count("SELECT COUNT(*) FROM bf_order WHERE created_at>=? AND created_at<?", start, endExclusive);
        long paidOrderCount = count("SELECT COUNT(DISTINCT order_id) FROM bf_payment WHERE status='SUCCESS' AND paid_at>=? AND paid_at<?", start, endExclusive);
        long revenue = count("SELECT COALESCE(SUM(amount_cent),0) FROM bf_payment WHERE status='SUCCESS' AND paid_at>=? AND paid_at<?", start, endExclusive);
        long refunds = count("SELECT COALESCE(SUM(amount_cent),0) FROM bf_refund WHERE status='SUCCESS' AND COALESCE(refunded_at,updated_at)>=? AND COALESCE(refunded_at,updated_at)<?", start, endExclusive);
        long newUsers = count("SELECT COUNT(*) FROM bf_user WHERE created_at>=? AND created_at<?", start, endExclusive);
        long verifications = count("SELECT COUNT(*) FROM bf_verification WHERE status='USED' AND verified_at>=? AND verified_at<?", start, endExclusive);

        Map<String, Long> statuses = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT status,COUNT(*) total FROM bf_order
                WHERE created_at>=? AND created_at<? GROUP BY status ORDER BY total DESC,status
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> statuses.put(rs.getString("status"), rs.getLong("total")), start, endExclusive);
        return new ReportOverviewResponse(period.start(), period.end(), orderCount, paidOrderCount, revenue,
                refunds, Math.max(0, revenue - refunds), newUsers, verifications, statuses, LocalDateTime.now());
    }

    public List<DailyReportItem> daily(LocalDate requestedStart, LocalDate requestedEnd) {
        Period period = period(requestedStart, requestedEnd);
        LocalDateTime start = period.start().atStartOfDay();
        LocalDateTime endExclusive = period.end().plusDays(1).atStartOfDay();

        Map<LocalDate, MutableDay> days = new LinkedHashMap<>();
        for (LocalDate day = period.start(); !day.isAfter(period.end()); day = day.plusDays(1)) {
            days.put(day, new MutableDay());
        }
        jdbcTemplate.query("""
                SELECT DATE(created_at) day,COUNT(*) total FROM bf_order
                WHERE created_at>=? AND created_at<? GROUP BY DATE(created_at)
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> days.get(rs.getObject("day", LocalDate.class)).orders = rs.getLong("total"), start, endExclusive);
        jdbcTemplate.query("""
                SELECT DATE(paid_at) day,COUNT(DISTINCT order_id) orders,COALESCE(SUM(amount_cent),0) amount
                FROM bf_payment WHERE status='SUCCESS' AND paid_at>=? AND paid_at<? GROUP BY DATE(paid_at)
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            MutableDay day = days.get(rs.getObject("day", LocalDate.class));
            day.paidOrders = rs.getLong("orders");
            day.revenue = rs.getLong("amount");
        }, start, endExclusive);
        jdbcTemplate.query("""
                SELECT DATE(COALESCE(refunded_at,updated_at)) day,COALESCE(SUM(amount_cent),0) amount
                FROM bf_refund WHERE status='SUCCESS' AND COALESCE(refunded_at,updated_at)>=?
                  AND COALESCE(refunded_at,updated_at)<? GROUP BY DATE(COALESCE(refunded_at,updated_at))
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> days.get(rs.getObject("day", LocalDate.class)).refunds = rs.getLong("amount"), start, endExclusive);
        jdbcTemplate.query("""
                SELECT DATE(created_at) day,COUNT(*) total FROM bf_user
                WHERE created_at>=? AND created_at<? GROUP BY DATE(created_at)
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> days.get(rs.getObject("day", LocalDate.class)).newUsers = rs.getLong("total"), start, endExclusive);
        jdbcTemplate.query("""
                SELECT DATE(verified_at) day,COUNT(*) total FROM bf_verification
                WHERE status='USED' AND verified_at>=? AND verified_at<? GROUP BY DATE(verified_at)
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> days.get(rs.getObject("day", LocalDate.class)).verifications = rs.getLong("total"), start, endExclusive);

        return days.entrySet().stream().map(entry -> {
            MutableDay value = entry.getValue();
            return new DailyReportItem(entry.getKey(), value.orders, value.paidOrders, value.revenue,
                    value.refunds, Math.max(0, value.revenue - value.refunds), value.newUsers, value.verifications);
        }).toList();
    }

    public List<ResourceSalesReportItem> resourceSales(LocalDate requestedStart, LocalDate requestedEnd, int limit) {
        Period period = period(requestedStart, requestedEnd);
        if (limit < 1 || limit > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "limit范围为1—100");
        }
        LocalDateTime start = period.start().atStartOfDay();
        LocalDateTime endExclusive = period.end().plusDays(1).atStartOfDay();
        return jdbcTemplate.query("""
                SELECT r.id resource_id,oi.resource_name,r.resource_type,COUNT(DISTINCT o.id) order_count,
                       COALESCE(SUM(oi.quantity),0) quantity,COALESCE(SUM(oi.amount_cent),0) gross_sales_cent
                FROM bf_order_item oi JOIN bf_order o ON o.id=oi.order_id
                JOIN bf_resource_sku sku ON sku.id=oi.sku_id JOIN bf_resource r ON r.id=sku.resource_id
                WHERE EXISTS (SELECT 1 FROM bf_payment p WHERE p.order_id=o.id AND p.status='SUCCESS'
                  AND p.paid_at>=? AND p.paid_at<?)
                GROUP BY r.id,oi.resource_name,r.resource_type
                ORDER BY gross_sales_cent DESC,quantity DESC,r.id LIMIT ?
                """, (rs, rowNum) -> new ResourceSalesReportItem(
                rs.getLong("resource_id"), rs.getString("resource_name"), rs.getString("resource_type"),
                rs.getLong("order_count"), rs.getLong("quantity"), rs.getLong("gross_sales_cent")),
                start, endExclusive, limit);
    }

    private long count(String sql, LocalDateTime start, LocalDateTime endExclusive) {
        return jdbcTemplate.queryForObject(sql, Long.class, start, endExclusive);
    }

    private Period period(LocalDate requestedStart, LocalDate requestedEnd) {
        LocalDate end = requestedEnd == null ? LocalDate.now() : requestedEnd;
        LocalDate start = requestedStart == null ? end.minusDays(29) : requestedStart;
        if (start.isAfter(end)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "报表日期范围不能超过366天");
        }
        return new Period(start, end);
    }

    private record Period(LocalDate start, LocalDate end) {
    }

    private static final class MutableDay {
        private long orders;
        private long paidOrders;
        private long revenue;
        private long refunds;
        private long newUsers;
        private long verifications;
    }
}
