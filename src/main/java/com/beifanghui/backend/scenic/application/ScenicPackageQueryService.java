package com.beifanghui.backend.scenic.application;

import com.beifanghui.backend.scenic.api.ScenicPackageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ScenicPackageQueryService {
    private final JdbcTemplate jdbcTemplate;

    public ScenicPackageQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ScenicPackageResponse detail(long packageSkuId) {
        List<PackageHead> rows = jdbcTemplate.query("""
                SELECT sku.id package_sku_id,p.id,p.package_code,p.name,p.description,p.price_cent,p.status
                FROM bf_resource_package p
                JOIN bf_resource_sku sku ON sku.sku_code=p.package_code AND sku.status='ACTIVE'
                JOIN bf_resource r ON r.id=sku.resource_id AND r.resource_type='SCENIC_TICKET' AND r.status='ACTIVE'
                WHERE sku.id=? AND p.status='ACTIVE'
                  AND (p.start_at IS NULL OR p.start_at<=CURRENT_TIMESTAMP)
                  AND (p.end_at IS NULL OR p.end_at>=CURRENT_TIMESTAMP)
                """, (rs, rowNum) -> new PackageHead(rs.getLong("package_sku_id"), rs.getLong("id"),
                rs.getString("package_code"), rs.getString("name"), rs.getString("description"),
                rs.getLong("price_cent"), rs.getString("status")), packageSkuId);
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "套票不存在或未上架");
        }
        PackageHead head = rows.get(0);
        List<ScenicPackageResponse.Component> components = jdbcTemplate.query("""
                SELECT sku.id,sku.sku_code,sku.name sku_name,r.name resource_name,r.resource_type,item.quantity
                FROM bf_resource_package_item item
                JOIN bf_resource_sku sku ON sku.id=item.sku_id AND sku.status='ACTIVE'
                JOIN bf_resource r ON r.id=sku.resource_id AND r.status='ACTIVE'
                WHERE item.package_id=? ORDER BY sku.id
                """, (rs, rowNum) -> new ScenicPackageResponse.Component(rs.getLong("id"),
                rs.getString("sku_code"), rs.getString("sku_name"), rs.getString("resource_name"),
                rs.getString("resource_type"), rs.getInt("quantity")), head.packageId());
        if (components.size() < 2) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "套票尚未配置完整组件");
        }
        return new ScenicPackageResponse(head.packageSkuId(), head.packageId(), head.packageCode(), head.name(),
                head.description(), head.priceCent(), "CNY", head.status(), components);
    }

    private record PackageHead(long packageSkuId, long packageId, String packageCode, String name,
                               String description, long priceCent, String status) {
    }
}
