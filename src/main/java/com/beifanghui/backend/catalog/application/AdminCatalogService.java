package com.beifanghui.backend.catalog.application;

import com.beifanghui.backend.catalog.api.AdminResourceResponse;
import com.beifanghui.backend.catalog.api.InventoryAdjustmentRequest;
import com.beifanghui.backend.catalog.api.InventoryAdjustmentResponse;
import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminCatalogService {
    private static final Set<String> RESOURCE_STATUSES = Set.of("ACTIVE", "INACTIVE", "DRAFT");
    private final JdbcTemplate jdbcTemplate;
    public AdminCatalogService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Transactional
    public AdminResourceResponse updateStatus(AuthenticatedPrincipal principal, long resourceId, String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "status不能为空");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!RESOURCE_STATUSES.contains(normalized)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "status仅支持ACTIVE、INACTIVE、DRAFT");
        }
        long operatorId = ensureOperator(principal);
        List<String> previous = jdbcTemplate.queryForList(
                "SELECT status FROM bf_resource WHERE id=? FOR UPDATE", String.class, resourceId);
        if (previous.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "资源不存在");
        jdbcTemplate.update("UPDATE bf_resource SET status=? WHERE id=?", normalized, resourceId);
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail)
                VALUES (?,'RESOURCE_STATUS_UPDATE','RESOURCE',?,JSON_OBJECT('fromStatus',?,'toStatus',?))
                """, operatorId, String.valueOf(resourceId), previous.get(0), normalized);
        return findResource(resourceId);
    }

    @Transactional
    public InventoryAdjustmentResponse adjustInventory(AuthenticatedPrincipal principal, long inventoryId,
                                                       String idempotencyKey, InventoryAdjustmentRequest request) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "Idempotency-Key不能为空且最长64字符");
        }
        if (request == null || request.delta() == null || request.delta() == 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "delta必须是非0整数");
        }
        if (Math.abs((long) request.delta()) > 100000) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "单次库存调整绝对值不能超过100000");
        }
        String logKey = "ADMIN-ADJUST:" + idempotencyKey;
        List<PreviousAdjustment> previousResult = jdbcTemplate.query("""
                SELECT inventory_id,quantity_delta,quantity_after FROM bf_inventory_log WHERE idempotency_key=?
                """, (rs,n) -> new PreviousAdjustment(rs.getLong("inventory_id"),
                rs.getInt("quantity_delta"),rs.getInt("quantity_after")), logKey);
        if (!previousResult.isEmpty()) {
            PreviousAdjustment previous=previousResult.get(0);
            if(previous.inventoryId()!=inventoryId||previous.delta()!=request.delta()) {
                throw new BusinessException(CommonErrorCode.CONFLICT,"相同Idempotency-Key不能用于不同库存或调整量");
            }
            return findInventory(inventoryId,previous.quantityAfter()-previous.delta(),previous.delta());
        }

        long operatorId = ensureOperator(principal);
        Inventory current = lockInventory(inventoryId);
        int after = current.availableQuantity() + request.delta();
        if (after < 0 || after > current.totalQuantity()) {
            throw new BusinessException(CommonErrorCode.INVENTORY_CONFLICT,
                    "调整后可用库存必须在0到总库存" + current.totalQuantity() + "之间");
        }
        jdbcTemplate.update("""
                UPDATE bf_inventory SET available_quantity=?,version=version+1 WHERE id=?
                """, after, inventoryId);
        String remark = normalizeRemark(request.remark());
        jdbcTemplate.update("""
                INSERT INTO bf_inventory_log
                (inventory_id,order_no,change_type,quantity_delta,quantity_after,idempotency_key,remark)
                VALUES (?,NULL,'ADMIN_ADJUST',?,?,?,?)
                """, inventoryId, request.delta(), after, logKey, remark);
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail)
                VALUES (?,'INVENTORY_ADJUST','INVENTORY',?,
                        JSON_OBJECT('delta',?,'beforeQuantity',?,'afterQuantity',?,'remark',?))
                """, operatorId, String.valueOf(inventoryId), request.delta(), current.availableQuantity(), after, remark);
        return findInventory(inventoryId, current.availableQuantity(), request.delta());
    }

    private Inventory lockInventory(long inventoryId) {
        List<Inventory> rows = jdbcTemplate.query("""
                SELECT id,sku_id,business_date,time_slot,total_quantity,available_quantity
                FROM bf_inventory WHERE id=? FOR UPDATE
                """, (rs,n) -> new Inventory(rs.getLong("id"),rs.getLong("sku_id"),
                rs.getObject("business_date",LocalDate.class),rs.getString("time_slot"),
                rs.getInt("total_quantity"),rs.getInt("available_quantity")), inventoryId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND,"库存记录不存在");
        return rows.get(0);
    }

    private InventoryAdjustmentResponse findInventory(long inventoryId, int before, int delta) {
        Inventory value = lockInventory(inventoryId);
        return new InventoryAdjustmentResponse(value.id(),value.skuId(),value.businessDate(),value.timeSlot(),
                value.totalQuantity(),before,value.availableQuantity(),delta);
    }

    private AdminResourceResponse findResource(long resourceId) {
        return jdbcTemplate.queryForObject("""
                SELECT id,resource_type,name,status,updated_at FROM bf_resource WHERE id=?
                """, (rs,n) -> new AdminResourceResponse(rs.getLong("id"),rs.getString("resource_type"),
                rs.getString("name"),rs.getString("status"),rs.getObject("updated_at",LocalDateTime.class)),resourceId);
    }

    private long ensureOperator(AuthenticatedPrincipal principal) {
        String openid="mock:"+principal.userId();
        jdbcTemplate.update("""
                INSERT INTO bf_user(wechat_openid,nickname,status) VALUES (?,?,'ACTIVE')
                ON DUPLICATE KEY UPDATE nickname=VALUES(nickname),status='ACTIVE'
                """,openid,principal.displayName());
        return jdbcTemplate.queryForObject("SELECT id FROM bf_user WHERE wechat_openid=?",Long.class,openid);
    }

    private String normalizeRemark(String value) {
        String result=StringUtils.hasText(value)?value.trim():"管理员人工调整";
        if(result.length()>255) throw new BusinessException(CommonErrorCode.INVALID_REQUEST,"remark最长255字符");
        return result;
    }

    private record Inventory(long id,long skuId,LocalDate businessDate,String timeSlot,
                             int totalQuantity,int availableQuantity) {}
    private record PreviousAdjustment(long inventoryId,int delta,int quantityAfter) {}
}
