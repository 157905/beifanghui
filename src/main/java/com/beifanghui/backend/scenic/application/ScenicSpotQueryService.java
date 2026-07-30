package com.beifanghui.backend.scenic.application;

import com.beifanghui.backend.scenic.api.ScenicSpotDetailResponse;
import com.beifanghui.backend.scenic.api.ScenicSpotSummaryResponse;
import com.beifanghui.backend.scenic.api.ScenicTicketResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ScenicSpotQueryService {
    private static final String SCENIC_TICKET = "SCENIC_TICKET";
    private final JdbcTemplate jdbcTemplate;

    public ScenicSpotQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<ScenicSpotSummaryResponse> list(String keyword, int page, int pageSize) {
        validatePage(page, pageSize);
        String pattern = StringUtils.hasText(keyword) ? "%" + keyword.trim() + "%" : "%";
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bf_resource r
                JOIN bf_business_site s ON s.id=r.site_id AND s.status='ACTIVE'
                WHERE r.resource_type=? AND r.status='ACTIVE'
                  AND (r.name LIKE ? OR COALESCE(r.description,'') LIKE ? OR COALESCE(s.address,'') LIKE ?)
                """, Long.class, SCENIC_TICKET, pattern, pattern, pattern);
        List<ScenicSpotSummaryResponse> items = jdbcTemplate.query("""
                SELECT r.id,r.site_id,r.name,r.category_code,r.description,r.cover_url,
                       s.address,s.service_phone,MIN(sku.price_cent) minimum_price_cent
                FROM bf_resource r
                JOIN bf_business_site s ON s.id=r.site_id AND s.status='ACTIVE'
                LEFT JOIN bf_resource_sku sku ON sku.resource_id=r.id AND sku.status='ACTIVE'
                WHERE r.resource_type=? AND r.status='ACTIVE'
                  AND (r.name LIKE ? OR COALESCE(r.description,'') LIKE ? OR COALESCE(s.address,'') LIKE ?)
                GROUP BY r.id,r.site_id,r.name,r.category_code,r.description,r.cover_url,s.address,s.service_phone
                ORDER BY r.id ASC LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ScenicSpotSummaryResponse(
                rs.getLong("id"), rs.getLong("site_id"), rs.getString("name"),
                rs.getString("category_code"), rs.getString("description"), rs.getString("cover_url"),
                rs.getString("address"), rs.getString("service_phone"),
                rs.getObject("minimum_price_cent", Long.class), "CNY"),
                SCENIC_TICKET, pattern, pattern, pattern, pageSize, (page - 1) * pageSize);
        long safeTotal = total == null ? 0 : total;
        int totalPages = safeTotal == 0 ? 0 : (int) ((safeTotal + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, safeTotal, totalPages);
    }

    public ScenicSpotDetailResponse detail(long scenicSpotId) {
        List<ScenicSpotDetailResponse> rows = jdbcTemplate.query("""
                SELECT r.id,r.site_id,r.name,r.category_code,r.description,r.cover_url,r.attributes,
                       s.site_code,s.address,s.longitude,s.latitude,s.service_phone,s.introduction
                FROM bf_resource r
                JOIN bf_business_site s ON s.id=r.site_id AND s.status='ACTIVE'
                WHERE r.id=? AND r.resource_type=? AND r.status='ACTIVE'
                """, (rs, rowNum) -> new ScenicSpotDetailResponse(
                rs.getLong("id"), rs.getLong("site_id"), rs.getString("site_code"), rs.getString("name"),
                rs.getString("category_code"), rs.getString("description"), rs.getString("cover_url"),
                rs.getString("address"), rs.getBigDecimal("longitude"), rs.getBigDecimal("latitude"),
                rs.getString("service_phone"), rs.getString("introduction"), rs.getString("attributes")),
                scenicSpotId, SCENIC_TICKET);
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区不存在或未上架");
        }
        return rows.get(0);
    }

    public List<ScenicTicketResponse> tickets(long scenicSpotId, LocalDate serviceDate) {
        if (serviceDate == null || serviceDate.isBefore(LocalDate.now())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "游玩日期不能早于今天");
        }
        if (serviceDate.isAfter(LocalDate.now().plusYears(1))) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "最多查询未来一年内的门票");
        }
        detail(scenicSpotId);
        return jdbcTemplate.query("""
                SELECT sku.id,sku.sku_code,sku.name,sku.price_cent default_price_cent,sku.attributes,
                       inv.time_slot,inv.available_quantity,inv.price_cent inventory_price_cent,
                       p.audience_rule,p.usage_rule,p.refund_rule,p.entry_notice,p.valid_days,
                       p.max_per_order,p.real_name_required,p.id_card_required
                FROM bf_resource_sku sku
                LEFT JOIN bf_inventory inv ON inv.sku_id=sku.id AND inv.business_date=?
                LEFT JOIN bf_ticket_profile p ON p.sku_id=sku.id
                WHERE sku.resource_id=? AND sku.status='ACTIVE'
                ORDER BY sku.price_cent ASC,sku.id ASC,inv.time_slot ASC
                """, (rs, rowNum) -> {
            Long inventoryPrice = rs.getObject("inventory_price_cent", Long.class);
            Integer availableQuantity = rs.getObject("available_quantity", Integer.class);
            boolean configured = availableQuantity != null;
            return new ScenicTicketResponse(
                    rs.getLong("id"), rs.getString("sku_code"), rs.getString("name"), serviceDate,
                    configured ? rs.getString("time_slot") : "",
                    inventoryPrice == null ? rs.getLong("default_price_cent") : inventoryPrice,
                    "CNY", configured && availableQuantity > 0,
                    configured ? availableQuantity : 0, configured,
                    rs.getString("audience_rule"), rs.getString("usage_rule"),
                    rs.getString("refund_rule"), rs.getString("entry_notice"),
                    rs.getObject("valid_days", Integer.class) == null ? 1 : rs.getInt("valid_days"),
                    rs.getObject("max_per_order", Integer.class), rs.getBoolean("real_name_required"),
                    rs.getBoolean("id_card_required"), rs.getString("attributes"));
        }, serviceDate, scenicSpotId);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page从1开始，pageSize范围为1—100");
        }
    }
}
