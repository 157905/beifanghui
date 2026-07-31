package com.beifanghui.backend.scenic.application;

import com.beifanghui.backend.scenic.api.ScenicOperationSummaryResponse;
import com.beifanghui.backend.scenic.api.ScenicVerificationRecordResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ScenicOperationQueryService {
    private static final int MAX_DATE_RANGE_DAYS = 31;
    private final JdbcTemplate jdbcTemplate;

    public ScenicOperationQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ScenicOperationSummaryResponse summary(long scenicSpotId, LocalDate startDate, LocalDate endDate) {
        DateRange range = normalizeDateRange(startDate, endDate);
        ScenicSpot spot = requireScenicSpot(scenicSpotId);
        SalesAggregate sales = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT o.id) paid_order_count, COALESCE(SUM(i.quantity), 0) sold_ticket_count,
                       COALESCE(SUM(i.amount_cent), 0) sales_amount_cent
                FROM bf_order_item i
                JOIN bf_order o ON o.id=i.order_id AND o.status IN ('PAID','READY','COMPLETED')
                JOIN bf_resource_sku sku ON sku.id=i.sku_id
                JOIN bf_resource resource ON resource.id=sku.resource_id
                WHERE resource.site_id=? AND resource.resource_type='SCENIC_TICKET'
                  AND i.service_date BETWEEN ? AND ?
                """, (rs, rowNum) -> new SalesAggregate(rs.getLong("paid_order_count"),
                rs.getLong("sold_ticket_count"), rs.getLong("sales_amount_cent")), spot.siteId(),
                range.startDate(), range.endDate());
        TicketAggregate tickets = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) issued_ticket_count,
                       COALESCE(SUM(CASE WHEN v.status='USED' THEN 1 ELSE 0 END), 0) verified_ticket_count
                FROM bf_verification v
                JOIN bf_order_item i ON i.id=v.order_item_id
                LEFT JOIN bf_order_package_item package_item ON package_item.id=v.order_package_item_id
                JOIN bf_resource_sku effective_sku ON effective_sku.id=COALESCE(package_item.component_sku_id, i.sku_id)
                JOIN bf_resource resource ON resource.id=effective_sku.resource_id
                WHERE resource.site_id=? AND resource.resource_type='SCENIC_TICKET'
                  AND i.service_date BETWEEN ? AND ? AND v.status IN ('UNUSED','USED')
                """, (rs, rowNum) -> new TicketAggregate(rs.getLong("issued_ticket_count"),
                rs.getLong("verified_ticket_count")), spot.siteId(), range.startDate(), range.endDate());
        double verificationRate = tickets.issuedTicketCount() == 0 ? 0D
                : Math.round(tickets.verifiedTicketCount() * 10000D / tickets.issuedTicketCount()) / 100D;
        return new ScenicOperationSummaryResponse(scenicSpotId, spot.name(), range.startDate(), range.endDate(),
                sales.paidOrderCount(), sales.soldTicketCount(), sales.salesAmountCent(), "CNY",
                tickets.issuedTicketCount(), tickets.verifiedTicketCount(), verificationRate);
    }

    public PageResponse<ScenicVerificationRecordResponse> verificationRecords(long scenicSpotId, LocalDate startDate,
                                                                                LocalDate endDate, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page最小为1，pageSize范围为1—100");
        }
        DateRange range = normalizeDateRange(startDate, endDate);
        ScenicSpot spot = requireScenicSpot(scenicSpotId);
        String from = """
                FROM bf_verification v
                JOIN bf_order_item i ON i.id=v.order_item_id
                JOIN bf_order o ON o.id=i.order_id
                LEFT JOIN bf_order_package_item package_item ON package_item.id=v.order_package_item_id
                JOIN bf_resource_sku effective_sku ON effective_sku.id=COALESCE(package_item.component_sku_id, i.sku_id)
                JOIN bf_resource resource ON resource.id=effective_sku.resource_id
                LEFT JOIN bf_user verifier ON verifier.id=v.verified_by
                LEFT JOIN bf_audit_log audit ON audit.action='VERIFICATION_CONSUME'
                    AND audit.target_type='VERIFICATION' AND audit.target_id=CAST(v.id AS CHAR)
                WHERE resource.site_id=? AND resource.resource_type='SCENIC_TICKET' AND v.status='USED'
                  AND v.verified_at>=? AND v.verified_at<DATE_ADD(?, INTERVAL 1 DAY)
                """;
        List<Object> args = List.of(spot.siteId(), range.startDate(), range.endDate());
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<ScenicVerificationRecordResponse> items = jdbcTemplate.query("""
                SELECT v.id verification_id,o.id order_id,o.order_no,i.id order_item_id,v.ticket_no,
                       COALESCE(package_item.component_sku_name,i.sku_name) ticket_name,
                       COALESCE(package_item.component_resource_name,i.resource_name) resource_name,
                       i.service_date,v.verified_at,v.verified_by,verifier.nickname verifier_name,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(audit.detail, '$.verificationChannel')), 'UNKNOWN') verification_channel
                """ + from + " ORDER BY v.verified_at DESC,v.id DESC LIMIT ? OFFSET ?", (rs, rowNum) ->
                new ScenicVerificationRecordResponse(rs.getLong("verification_id"), rs.getLong("order_id"),
                        rs.getString("order_no"), rs.getLong("order_item_id"), rs.getInt("ticket_no"),
                        rs.getString("ticket_name"), rs.getString("resource_name"),
                        rs.getObject("service_date", LocalDate.class), rs.getObject("verified_at", LocalDateTime.class),
                        rs.getObject("verified_by", Long.class), rs.getString("verifier_name"),
                        rs.getString("verification_channel")), pageArgs.toArray());
        long resultTotal = total == null ? 0 : total;
        int totalPages = resultTotal == 0 ? 0 : (int) ((resultTotal + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, resultTotal, totalPages);
    }

    private ScenicSpot requireScenicSpot(long scenicSpotId) {
        List<ScenicSpot> spots = jdbcTemplate.query("""
                SELECT id,site_id,name FROM bf_resource
                WHERE id=? AND resource_type='SCENIC_TICKET'
                """, (rs, rowNum) -> new ScenicSpot(rs.getLong("id"), rs.getLong("site_id"),
                rs.getString("name")), scenicSpotId);
        if (spots.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区不存在");
        }
        return spots.get(0);
    }

    private DateRange normalizeDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now() : startDate;
        LocalDate end = endDate == null ? start : endDate;
        if (end.isBefore(start) || end.toEpochDay() - start.toEpochDay() >= MAX_DATE_RANGE_DAYS) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "日期范围须为正序且最多31天");
        }
        return new DateRange(start, end);
    }

    private record ScenicSpot(long id, long siteId, String name) {
    }

    private record SalesAggregate(long paidOrderCount, long soldTicketCount, long salesAmountCent) {
    }

    private record TicketAggregate(long issuedTicketCount, long verifiedTicketCount) {
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
