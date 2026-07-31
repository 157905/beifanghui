package com.beifanghui.backend.catalog.application;

import com.beifanghui.backend.catalog.api.AdminResourceResponse;
import com.beifanghui.backend.catalog.api.AdminResourceListItem;
import com.beifanghui.backend.catalog.api.AdminResourceUpsertRequest;
import com.beifanghui.backend.catalog.api.AdminInventoryResponse;
import com.beifanghui.backend.catalog.api.AdminSkuResponse;
import com.beifanghui.backend.catalog.api.AdminSkuUpsertRequest;
import com.beifanghui.backend.catalog.api.InventorySetupRequest;
import com.beifanghui.backend.catalog.api.InventoryAdjustmentRequest;
import com.beifanghui.backend.catalog.api.InventoryAdjustmentResponse;
import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import com.beifanghui.backend.shared.api.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminCatalogService {
    private static final Set<String> RESOURCE_STATUSES = Set.of("ACTIVE", "INACTIVE", "DRAFT");
    private final JdbcTemplate jdbcTemplate;
    public AdminCatalogService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public PageResponse<AdminResourceListItem> listResources(String type, String status, String keyword,
                                                              int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page 从 1 开始，pageSize 范围为 1-100");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(type)) {
            where.append(" AND r.resource_type=?");
            args.add(type.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!RESOURCE_STATUSES.contains(normalized)) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "资源状态不合法");
            }
            where.append(" AND r.status=?");
            args.add(normalized);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (r.name LIKE ? OR r.category_code LIKE ?)");
            String search = "%" + keyword.trim() + "%";
            args.add(search);
            args.add(search);
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_resource r" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<AdminResourceListItem> items = jdbcTemplate.query("""
                SELECT r.id,r.site_id,r.resource_type,r.category_code,r.name,r.description,r.cover_url,r.status,
                       COUNT(s.id) sku_count,r.updated_at
                FROM bf_resource r LEFT JOIN bf_resource_sku s ON s.resource_id=r.id
                """ + where + " GROUP BY r.id,r.site_id,r.resource_type,r.category_code,r.name,r.description,r.cover_url,r.status,r.updated_at"
                + " ORDER BY r.id DESC LIMIT ? OFFSET ?", (rs, n) -> toResourceItem(rs), pageArgs.toArray());
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, total, totalPages);
    }

    @Transactional
    public AdminResourceListItem createResource(AuthenticatedPrincipal principal, AdminResourceUpsertRequest request) {
        ResourceValues values = validateResource(request);
        long operatorId = ensureOperator(principal);
        jdbcTemplate.update("""
                INSERT INTO bf_resource(site_id,resource_type,category_code,name,description,cover_url,status,attributes)
                VALUES (?,?,?,?,?,?,?,JSON_OBJECT())
                """, values.siteId(), values.resourceType(), values.categoryCode(), values.name(),
                values.description(), values.coverUrl(), values.status());
        long resourceId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        audit(operatorId, "RESOURCE_CREATE", resourceId, values.status());
        return findResourceItem(resourceId);
    }

    @Transactional
    public AdminResourceListItem updateResource(AuthenticatedPrincipal principal, long resourceId,
                                                AdminResourceUpsertRequest request) {
        ResourceValues values = validateResource(request);
        long operatorId = ensureOperator(principal);
        int changed = jdbcTemplate.update("""
                UPDATE bf_resource SET site_id=?,resource_type=?,category_code=?,name=?,description=?,cover_url=?,
                status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, values.siteId(), values.resourceType(), values.categoryCode(), values.name(),
                values.description(), values.coverUrl(), values.status(), resourceId);
        if (changed == 0) throw new BusinessException(CommonErrorCode.NOT_FOUND, "资源不存在");
        audit(operatorId, "RESOURCE_UPDATE", resourceId, values.status());
        return findResourceItem(resourceId);
    }

    public List<AdminInventoryResponse> listInventories(long resourceId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_resource WHERE id=?", Integer.class, resourceId);
        if (count == null || count == 0) throw new BusinessException(CommonErrorCode.NOT_FOUND, "资源不存在");
        return jdbcTemplate.query("""
                SELECT i.id inventory_id,s.id sku_id,s.sku_code,s.name sku_name,s.status sku_status,
                       i.business_date,i.time_slot,i.total_quantity,i.available_quantity,i.price_cent
                FROM bf_resource_sku s JOIN bf_inventory i ON i.sku_id=s.id
                WHERE s.resource_id=? ORDER BY s.id DESC,i.business_date DESC,i.time_slot
                """, (rs, n) -> new AdminInventoryResponse(
                rs.getLong("inventory_id"), rs.getLong("sku_id"), rs.getString("sku_code"),
                rs.getString("sku_name"), rs.getString("sku_status"),
                rs.getObject("business_date", LocalDate.class), rs.getString("time_slot"),
                rs.getInt("total_quantity"), rs.getInt("available_quantity"), rs.getLong("price_cent")), resourceId);
    }

    public List<AdminSkuResponse> listSkus(long resourceId) {
        ensureResourceExists(resourceId);
        return jdbcTemplate.query("""
                SELECT id,resource_id,sku_code,name,price_cent,status,attributes
                FROM bf_resource_sku WHERE resource_id=? ORDER BY id DESC
                """, (rs, n) -> toSkuResponse(rs), resourceId);
    }

    @Transactional
    public AdminSkuResponse createSku(AuthenticatedPrincipal principal, long resourceId, AdminSkuUpsertRequest request) {
        ensureResourceExists(resourceId);
        SkuValues values = validateSku(request);
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_resource_sku WHERE sku_code=?", Integer.class, values.skuCode());
        if (existing != null && existing > 0) throw new BusinessException(CommonErrorCode.CONFLICT, "SKU 编码已存在");
        jdbcTemplate.update("""
                INSERT INTO bf_resource_sku(resource_id,sku_code,name,price_cent,status,attributes)
                VALUES (?,?,?,?,?,JSON_OBJECT())
                """, resourceId, values.skuCode(), values.name(), values.priceCent(), values.status());
        long skuId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditSku(ensureOperator(principal), "SKU_CREATE", skuId, values.status());
        return findSku(resourceId, skuId);
    }

    @Transactional
    public AdminSkuResponse updateSku(AuthenticatedPrincipal principal, long resourceId, long skuId,
                                      AdminSkuUpsertRequest request) {
        ensureResourceExists(resourceId);
        SkuValues values = validateSku(request);
        Integer duplicate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bf_resource_sku WHERE sku_code=? AND id<>?", Integer.class, values.skuCode(), skuId);
        if (duplicate != null && duplicate > 0) throw new BusinessException(CommonErrorCode.CONFLICT, "SKU 编码已存在");
        int changed = jdbcTemplate.update("""
                UPDATE bf_resource_sku SET sku_code=?,name=?,price_cent=?,status=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND resource_id=?
                """, values.skuCode(), values.name(), values.priceCent(), values.status(), skuId, resourceId);
        if (changed == 0) throw new BusinessException(CommonErrorCode.NOT_FOUND, "SKU 不存在或不属于该资源");
        auditSku(ensureOperator(principal), "SKU_UPDATE", skuId, values.status());
        return findSku(resourceId, skuId);
    }

    @Transactional
    public AdminInventoryResponse createInventory(AuthenticatedPrincipal principal, long resourceId,
                                                  InventorySetupRequest request) {
        ensureResourceExists(resourceId);
        InventoryValues values = validateInventory(resourceId, request);
        Integer existing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bf_inventory WHERE sku_id=? AND business_date=? AND time_slot=?
                """, Integer.class, values.skuId(), values.businessDate(), values.timeSlot());
        if (existing != null && existing > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该 SKU 在该日期和时段的库存已存在，请使用库存调整功能");
        }
        jdbcTemplate.update("""
                INSERT INTO bf_inventory(sku_id,business_date,time_slot,total_quantity,available_quantity,price_cent,version)
                VALUES (?,?,?,?,?,?,0)
                """, values.skuId(), values.businessDate(), values.timeSlot(), values.totalQuantity(),
                values.availableQuantity(), values.priceCent());
        long inventoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auditInventory(ensureOperator(principal), "INVENTORY_CREATE", inventoryId, values.totalQuantity(), values.availableQuantity());
        return findInventoryRecord(inventoryId);
    }

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

    private AdminResourceListItem findResourceItem(long resourceId) {
        List<AdminResourceListItem> items = jdbcTemplate.query("""
                SELECT r.id,r.site_id,r.resource_type,r.category_code,r.name,r.description,r.cover_url,r.status,
                       COUNT(s.id) sku_count,r.updated_at
                FROM bf_resource r LEFT JOIN bf_resource_sku s ON s.resource_id=r.id
                WHERE r.id=?
                GROUP BY r.id,r.site_id,r.resource_type,r.category_code,r.name,r.description,r.cover_url,r.status,r.updated_at
                """, (rs, n) -> toResourceItem(rs), resourceId);
        if (items.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "资源不存在");
        return items.get(0);
    }

    private AdminResourceListItem toResourceItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdminResourceListItem(rs.getLong("id"), rs.getObject("site_id", Long.class),
                rs.getString("resource_type"), rs.getString("category_code"), rs.getString("name"),
                rs.getString("description"), rs.getString("cover_url"), rs.getString("status"),
                rs.getLong("sku_count"), rs.getObject("updated_at", LocalDateTime.class));
    }

    private AdminSkuResponse findSku(long resourceId, long skuId) {
        List<AdminSkuResponse> result = jdbcTemplate.query("""
                SELECT id,resource_id,sku_code,name,price_cent,status,attributes
                FROM bf_resource_sku WHERE id=? AND resource_id=?
                """, (rs, n) -> toSkuResponse(rs), skuId, resourceId);
        if (result.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "SKU 不存在或不属于该资源");
        return result.get(0);
    }

    private AdminSkuResponse toSkuResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdminSkuResponse(rs.getLong("id"), rs.getLong("resource_id"), rs.getString("sku_code"),
                rs.getString("name"), rs.getLong("price_cent"), rs.getString("status"), rs.getString("attributes"));
    }

    private AdminInventoryResponse findInventoryRecord(long inventoryId) {
        List<AdminInventoryResponse> result = jdbcTemplate.query("""
                SELECT i.id inventory_id,s.id sku_id,s.sku_code,s.name sku_name,s.status sku_status,
                       i.business_date,i.time_slot,i.total_quantity,i.available_quantity,i.price_cent
                FROM bf_inventory i JOIN bf_resource_sku s ON s.id=i.sku_id WHERE i.id=?
                """, (rs, n) -> new AdminInventoryResponse(
                rs.getLong("inventory_id"), rs.getLong("sku_id"), rs.getString("sku_code"),
                rs.getString("sku_name"), rs.getString("sku_status"),
                rs.getObject("business_date", LocalDate.class), rs.getString("time_slot"),
                rs.getInt("total_quantity"), rs.getInt("available_quantity"), rs.getLong("price_cent")), inventoryId);
        if (result.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "库存记录不存在");
        return result.get(0);
    }

    private void ensureResourceExists(long resourceId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_resource WHERE id=?", Integer.class, resourceId);
        if (count == null || count == 0) throw new BusinessException(CommonErrorCode.NOT_FOUND, "资源不存在");
    }

    private SkuValues validateSku(AdminSkuUpsertRequest request) {
        if (request == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "请求体不能为空");
        String code = required(request.skuCode(), "skuCode", 64).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]*")) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "skuCode 仅支持大写字母、数字、下划线和连字符");
        }
        if (request.priceCent() == null || request.priceCent() < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "priceCent 必须是大于等于 0 的整数（单位：分）");
        }
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase(Locale.ROOT) : "DRAFT";
        if (!RESOURCE_STATUSES.contains(status)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "SKU 状态仅支持 ACTIVE、INACTIVE、DRAFT");
        }
        return new SkuValues(code, required(request.name(), "name", 200), request.priceCent(), status);
    }

    private InventoryValues validateInventory(long resourceId, InventorySetupRequest request) {
        if (request == null || request.skuId() == null || request.businessDate() == null || request.totalQuantity() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "skuId、businessDate 和 totalQuantity 不能为空");
        }
        Integer skuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bf_resource_sku WHERE id=? AND resource_id=?", Integer.class,
                request.skuId(), resourceId);
        if (skuCount == null || skuCount == 0) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "SKU 不存在或不属于该资源");
        int total = request.totalQuantity();
        int available = request.availableQuantity() == null ? total : request.availableQuantity();
        if (total < 0 || available < 0 || available > total) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "库存必须满足 0 ≤ 可用库存 ≤ 总库存");
        }
        String slot = optional(request.timeSlot(), 64);
        long price;
        if (request.priceCent() == null) {
            price = jdbcTemplate.queryForObject("SELECT price_cent FROM bf_resource_sku WHERE id=?", Long.class, request.skuId());
        } else {
            price = request.priceCent();
        }
        if (price < 0) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "priceCent 必须大于等于 0");
        return new InventoryValues(request.skuId(), request.businessDate(), slot == null ? "" : slot, total, available, price);
    }

    private ResourceValues validateResource(AdminResourceUpsertRequest request) {
        if (request == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "请求体不能为空");
        String type = required(request.resourceType(), "resourceType", 32).toUpperCase(Locale.ROOT);
        String name = required(request.name(), "name", 200);
        String categoryCode = optional(request.categoryCode(), 50);
        String description = optional(request.description(), 5000);
        String coverUrl = optional(request.coverUrl(), 512);
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase(Locale.ROOT) : "DRAFT";
        if (!RESOURCE_STATUSES.contains(status)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "资源状态仅支持 ACTIVE、INACTIVE、DRAFT");
        }
        return new ResourceValues(request.siteId(), type, categoryCode, name, description, coverUrl, status);
    }

    private String required(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, field + " 不能为空");
        String result = value.trim();
        if (result.length() > maxLength) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, field + " 长度不能超过 " + maxLength);
        return result;
    }

    private String optional(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String result = value.trim();
        if (result.length() > maxLength) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "字段长度不能超过 " + maxLength);
        return result;
    }

    private void audit(long operatorId, String action, long resourceId, String status) {
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail)
                VALUES (?,?,?,?,JSON_OBJECT('status',?))
                """, operatorId, action, "RESOURCE", String.valueOf(resourceId), status);
    }

    private void auditSku(long operatorId, String action, long skuId, String status) {
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail)
                VALUES (?,?,?,?,JSON_OBJECT('status',?))
                """, operatorId, action, "SKU", String.valueOf(skuId), status);
    }

    private void auditInventory(long operatorId, String action, long inventoryId, int total, int available) {
        jdbcTemplate.update("""
                INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail)
                VALUES (?,?,?,?,JSON_OBJECT('totalQuantity',?,'availableQuantity',?))
                """, operatorId, action, "INVENTORY", String.valueOf(inventoryId), total, available);
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
    private record ResourceValues(Long siteId, String resourceType, String categoryCode, String name,
                                  String description, String coverUrl, String status) {}
    private record SkuValues(String skuCode, String name, long priceCent, String status) {}
    private record InventoryValues(long skuId, LocalDate businessDate, String timeSlot,
                                   int totalQuantity, int availableQuantity, long priceCent) {}
}
