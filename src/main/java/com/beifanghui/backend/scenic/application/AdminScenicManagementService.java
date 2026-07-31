package com.beifanghui.backend.scenic.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.scenic.api.AdminScenicSpotCreateRequest;
import com.beifanghui.backend.scenic.api.AdminScenicSpotResponse;
import com.beifanghui.backend.scenic.api.AdminScenicSpotUpdateRequest;
import com.beifanghui.backend.scenic.api.AdminScenicTicketCreateRequest;
import com.beifanghui.backend.scenic.api.AdminScenicTicketResponse;
import com.beifanghui.backend.scenic.api.AdminScenicTicketUpdateRequest;
import com.beifanghui.backend.scenic.api.PackageComponentRequest;
import com.beifanghui.backend.scenic.api.PackageComponentUpdateRequest;
import com.beifanghui.backend.scenic.api.ScenicPackageResponse;
import com.beifanghui.backend.scenic.api.ScenicInventorySetupRequest;
import com.beifanghui.backend.scenic.api.ScenicInventorySetupResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminScenicManagementService {
    private static final String SCENIC_TICKET = "SCENIC_TICKET";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE");
    private static final Set<String> TICKET_TYPES = Set.of("ADULT", "CHILD", "PACKAGE", "SERVICE", "OTHER");
    private final JdbcTemplate jdbcTemplate;

    public AdminScenicManagementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AdminScenicSpotResponse createScenicSpot(AuthenticatedPrincipal principal,
                                                    AdminScenicSpotCreateRequest request) {
        validateCreateSpot(request);
        if (!jdbcTemplate.queryForList("SELECT id FROM bf_business_site WHERE site_code=?", Long.class,
                request.siteCode().trim().toUpperCase(Locale.ROOT)).isEmpty()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "景区编码已存在");
        }
        long operatorId = ensureOperator(principal);
        long siteId = insertSite(request);
        long scenicSpotId = insertScenicSpot(siteId, request);
        audit(operatorId, "SCENIC_SPOT_CREATE", "SCENIC_SPOT", scenicSpotId,
                "JSON_OBJECT('siteCode', ?)", request.siteCode().trim().toUpperCase(Locale.ROOT));
        return findSpot(scenicSpotId);
    }

    @Transactional
    public AdminScenicSpotResponse updateScenicSpot(AuthenticatedPrincipal principal, long scenicSpotId,
                                                    AdminScenicSpotUpdateRequest request) {
        validateUpdateSpot(request);
        ScenicSpotLocked current = lockScenicSpot(scenicSpotId);
        long operatorId = ensureOperator(principal);
        jdbcTemplate.update("""
                UPDATE bf_business_site
                SET name=?,address=?,longitude=?,latitude=?,service_phone=?,introduction=?,status=?
                WHERE id=?
                """, request.name().trim(), nullableText(request.address(), 255), request.longitude(), request.latitude(),
                nullableText(request.servicePhone(), 32), nullableText(request.introduction(), 65535),
                normalizeStatus(request.status()), current.siteId());
        jdbcTemplate.update("""
                UPDATE bf_resource
                SET category_code=?,name=?,description=?,cover_url=?,status=?,
                    attributes=JSON_OBJECT('openingHours', ?, 'recommendedDurationMinutes', ?)
                WHERE id=?
                """, nullableText(request.categoryCode(), 50), request.name().trim(),
                nullableText(request.description(), 65535), nullableText(request.coverUrl(), 512),
                normalizeStatus(request.status()), nullableText(request.openingHours(), 100),
                request.recommendedDurationMinutes(), scenicSpotId);
        audit(operatorId, "SCENIC_SPOT_UPDATE", "SCENIC_SPOT", scenicSpotId,
                "JSON_OBJECT('siteCode', ?)", current.siteCode());
        return findSpot(scenicSpotId);
    }

    @Transactional
    public AdminScenicTicketResponse createTicket(AuthenticatedPrincipal principal, long scenicSpotId,
                                                  AdminScenicTicketCreateRequest request) {
        validateCreateTicket(request);
        lockScenicSpot(scenicSpotId);
        String skuCode = request.skuCode().trim().toUpperCase(Locale.ROOT);
        if (!jdbcTemplate.queryForList("SELECT id FROM bf_resource_sku WHERE sku_code=?", Long.class, skuCode).isEmpty()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "票种编码已存在");
        }
        long operatorId = ensureOperator(principal);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_resource_sku
                    (resource_id,sku_code,name,price_cent,status,attributes)
                    VALUES (?,?,?,?,?,JSON_OBJECT('ticketType', ?))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, scenicSpotId);
            statement.setString(2, skuCode);
            statement.setString(3, request.name().trim());
            statement.setLong(4, request.priceCent());
            statement.setString(5, normalizeStatus(request.status()));
            statement.setString(6, normalizeTicketType(request.ticketType()));
            return statement;
        }, keys);
        long skuId = requireGeneratedId(keys, "票种创建失败");
        upsertTicketProfile(skuId, request.audienceRule(), request.usageRule(), request.refundRule(),
                request.entryNotice(), request.validDays(), request.maxPerOrder(), request.realNameRequired(),
                request.idCardRequired());
        if ("PACKAGE".equals(normalizeTicketType(request.ticketType()))) {
            createPackageDefinition(skuId, skuCode, request.name().trim(), request.priceCent(),
                    normalizeStatus(request.status()));
        }
        audit(operatorId, "SCENIC_TICKET_CREATE", "SCENIC_TICKET", skuId,
                "JSON_OBJECT('scenicSpotId', ?, 'skuCode', ?)", scenicSpotId, skuCode);
        return findTicket(skuId);
    }

    @Transactional(readOnly = true)
    public List<AdminScenicTicketResponse> listTickets(AuthenticatedPrincipal principal, long scenicSpotId) {
        lockScenicSpot(scenicSpotId);
        return jdbcTemplate.query(ticketSelect() + " sku.resource_id=? ORDER BY sku.id", ticketMapper(), scenicSpotId);
    }

    @Transactional
    public AdminScenicTicketResponse updateTicket(AuthenticatedPrincipal principal, long skuId,
                                                  AdminScenicTicketUpdateRequest request) {
        validateUpdateTicket(request);
        ScenicSkuLocked current = lockScenicSku(skuId);
        AdminScenicTicketResponse currentTicket = findTicket(skuId);
        if ("PACKAGE".equals(currentTicket.ticketType()) != "PACKAGE".equals(normalizeTicketType(request.ticketType()))) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "已创建的票种不能在普通票与套票之间转换");
        }
        long operatorId = ensureOperator(principal);
        jdbcTemplate.update("""
                UPDATE bf_resource_sku
                SET name=?,price_cent=?,status=?,attributes=JSON_OBJECT('ticketType', ?)
                WHERE id=?
                """, request.name().trim(), request.priceCent(), normalizeStatus(request.status()),
                normalizeTicketType(request.ticketType()), skuId);
        upsertTicketProfile(skuId, request.audienceRule(), request.usageRule(), request.refundRule(),
                request.entryNotice(), request.validDays(), request.maxPerOrder(), request.realNameRequired(),
                request.idCardRequired());
        if ("PACKAGE".equals(currentTicket.ticketType())) {
            jdbcTemplate.update("""
                    UPDATE bf_resource_package SET name=?,price_cent=?,status=? WHERE package_code=?
                    """, request.name().trim(), request.priceCent(), normalizeStatus(request.status()), current.skuCode());
        }
        audit(operatorId, "SCENIC_TICKET_UPDATE", "SCENIC_TICKET", skuId,
                "JSON_OBJECT('scenicSpotId', ?, 'skuCode', ?)", current.scenicSpotId(), current.skuCode());
        return findTicket(skuId);
    }

    @Transactional(readOnly = true)
    public ScenicPackageResponse packageDetail(AuthenticatedPrincipal principal, long packageSkuId) {
        lockPackageSku(packageSkuId);
        return findPackageDetail(packageSkuId);
    }

    @Transactional
    public ScenicPackageResponse replacePackageComponents(AuthenticatedPrincipal principal, long packageSkuId,
                                                          PackageComponentUpdateRequest request) {
        ScenicSkuLocked packageSku = lockPackageSku(packageSkuId);
        validatePackageComponents(packageSkuId, request);
        long packageId = packageId(packageSkuId);
        List<PackageComponent> components = request.components().stream()
                .map(component -> requirePackageComponent(packageSkuId, component))
                .toList();
        long operatorId = ensureOperator(principal);
        jdbcTemplate.update("DELETE FROM bf_resource_package_item WHERE package_id=?", packageId);
        for (PackageComponent component : components) {
            jdbcTemplate.update("""
                    INSERT INTO bf_resource_package_item(package_id,sku_id,quantity) VALUES (?,?,?)
                    """, packageId, component.skuId(), component.quantity());
        }
        audit(operatorId, "SCENIC_PACKAGE_COMPONENTS_UPDATE", "SCENIC_PACKAGE", packageSkuId,
                "JSON_OBJECT('packageCode', ?, 'componentCount', ?)", packageSku.skuCode(), components.size());
        return findPackageDetail(packageSkuId);
    }

    @Transactional
    public ScenicInventorySetupResponse setupInventory(AuthenticatedPrincipal principal, long skuId,
                                                        LocalDate businessDate, String timeSlot,
                                                        String idempotencyKey, ScenicInventorySetupRequest request) {
        validateInventorySetup(businessDate, timeSlot, idempotencyKey, request);
        lockScenicSku(skuId);
        String slot = normalizeSlot(timeSlot);
        String logKey = "SCENIC-SLOT:" + idempotencyKey.trim();
        List<Long> duplicate = jdbcTemplate.queryForList(
                "SELECT inventory_id FROM bf_inventory_log WHERE idempotency_key=?", Long.class, logKey);
        if (!duplicate.isEmpty()) {
            ScenicInventorySetupResponse previous = findInventory(duplicate.get(0));
            if (previous.skuId() != skuId || !previous.businessDate().equals(businessDate)
                    || !previous.timeSlot().equals(slot)) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "相同Idempotency-Key不能用于不同预约时段");
            }
            return previous;
        }

        long operatorId = ensureOperator(principal);
        List<InventoryLocked> rows = jdbcTemplate.query("""
                SELECT id,total_quantity,available_quantity
                FROM bf_inventory WHERE sku_id=? AND business_date=? AND time_slot=? FOR UPDATE
                """, (rs, rowNum) -> new InventoryLocked(rs.getLong("id"), rs.getInt("total_quantity"),
                rs.getInt("available_quantity")), skuId, businessDate, slot);
        long inventoryId;
        int reservedQuantity;
        int availableQuantity;
        if (rows.isEmpty()) {
            inventoryId = insertInventory(skuId, businessDate, slot, request.totalQuantity(), request.priceCent());
            reservedQuantity = 0;
            availableQuantity = request.totalQuantity();
        } else {
            InventoryLocked current = rows.get(0);
            reservedQuantity = current.totalQuantity() - current.availableQuantity();
            if (request.totalQuantity() < reservedQuantity) {
                throw new BusinessException(CommonErrorCode.INVENTORY_CONFLICT,
                        "该时段已有" + reservedQuantity + "个名额被预订，库存总数不能低于已预订数量");
            }
            inventoryId = current.id();
            availableQuantity = request.totalQuantity() - reservedQuantity;
            jdbcTemplate.update("""
                    UPDATE bf_inventory
                    SET total_quantity=?,available_quantity=?,price_cent=?,version=version+1 WHERE id=?
                    """, request.totalQuantity(), availableQuantity, request.priceCent(), inventoryId);
        }
        jdbcTemplate.update("""
                INSERT INTO bf_inventory_log
                (inventory_id,order_no,change_type,quantity_delta,quantity_after,idempotency_key,remark)
                VALUES (?,NULL,'SCENIC_SLOT_CONFIG',?,?,?,'后台设置景区预约时段库存')
                """, inventoryId, availableQuantity - reservedQuantity, availableQuantity, logKey);
        audit(operatorId, "SCENIC_SLOT_CONFIG", "INVENTORY", inventoryId,
                "JSON_OBJECT('skuId', ?, 'businessDate', ?, 'timeSlot', ?, 'totalQuantity', ?)",
                skuId, businessDate.toString(), slot, request.totalQuantity());
        return findInventory(inventoryId);
    }

    private long insertSite(AdminScenicSpotCreateRequest request) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_business_site
                    (site_code,name,site_type,address,longitude,latitude,service_phone,introduction,status)
                    VALUES (?,?,'SCENIC',?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.siteCode().trim().toUpperCase(Locale.ROOT));
            statement.setString(2, request.name().trim());
            statement.setString(3, nullableText(request.address(), 255));
            statement.setBigDecimal(4, request.longitude());
            statement.setBigDecimal(5, request.latitude());
            statement.setString(6, nullableText(request.servicePhone(), 32));
            statement.setString(7, nullableText(request.introduction(), 65535));
            statement.setString(8, normalizeStatus(request.status()));
            return statement;
        }, keys);
        return requireGeneratedId(keys, "景区场所创建失败");
    }

    private long insertScenicSpot(long siteId, AdminScenicSpotCreateRequest request) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_resource
                    (site_id,resource_type,category_code,name,description,cover_url,status,attributes)
                    VALUES (?,'SCENIC_TICKET',?,?,?,?,?,
                            JSON_OBJECT('openingHours', ?, 'recommendedDurationMinutes', ?))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, siteId);
            statement.setString(2, nullableText(request.categoryCode(), 50));
            statement.setString(3, request.name().trim());
            statement.setString(4, nullableText(request.description(), 65535));
            statement.setString(5, nullableText(request.coverUrl(), 512));
            statement.setString(6, normalizeStatus(request.status()));
            statement.setString(7, nullableText(request.openingHours(), 100));
            statement.setObject(8, request.recommendedDurationMinutes());
            return statement;
        }, keys);
        return requireGeneratedId(keys, "景区资源创建失败");
    }

    private long insertInventory(long skuId, LocalDate businessDate, String timeSlot, int totalQuantity, long priceCent) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bf_inventory
                    (sku_id,business_date,time_slot,total_quantity,available_quantity,price_cent,version)
                    VALUES (?,?,?,?,?,?,0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, skuId);
            statement.setObject(2, businessDate);
            statement.setString(3, timeSlot);
            statement.setInt(4, totalQuantity);
            statement.setInt(5, totalQuantity);
            statement.setLong(6, priceCent);
            return statement;
        }, keys);
        return requireGeneratedId(keys, "景区库存创建失败");
    }

    private void upsertTicketProfile(long skuId, String audienceRule, String usageRule, String refundRule,
                                     String entryNotice, Integer validDays, Integer maxPerOrder,
                                     Boolean realNameRequired, Boolean idCardRequired) {
        jdbcTemplate.update("""
                INSERT INTO bf_ticket_profile
                (sku_id,audience_rule,usage_rule,refund_rule,entry_notice,valid_days,max_per_order,
                 real_name_required,id_card_required)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE audience_rule=VALUES(audience_rule),usage_rule=VALUES(usage_rule),
                    refund_rule=VALUES(refund_rule),entry_notice=VALUES(entry_notice),valid_days=VALUES(valid_days),
                    max_per_order=VALUES(max_per_order),real_name_required=VALUES(real_name_required),
                    id_card_required=VALUES(id_card_required)
                """, skuId, nullableText(audienceRule, 500), usageRule.trim(), nullableText(refundRule, 65535),
                nullableText(entryNotice, 65535), validDays, maxPerOrder, realNameRequired, idCardRequired);
    }

    private void createPackageDefinition(long packageSkuId, String packageCode, String name, long priceCent,
                                         String status) {
        jdbcTemplate.update("""
                INSERT INTO bf_resource_package(package_code,name,description,price_cent,status)
                VALUES (?,?,NULL,?,?)
                """, packageCode, name, priceCent, status);
    }

    private ScenicSpotLocked lockScenicSpot(long scenicSpotId) {
        List<ScenicSpotLocked> rows = jdbcTemplate.query("""
                SELECT r.id,r.site_id,s.site_code FROM bf_resource r JOIN bf_business_site s ON s.id=r.site_id
                WHERE r.id=? AND r.resource_type=? FOR UPDATE
                """, (rs, rowNum) -> new ScenicSpotLocked(rs.getLong("id"), rs.getLong("site_id"),
                rs.getString("site_code")), scenicSpotId, SCENIC_TICKET);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区不存在");
        return rows.get(0);
    }

    private ScenicSkuLocked lockScenicSku(long skuId) {
        List<ScenicSkuLocked> rows = jdbcTemplate.query("""
                SELECT sku.id,sku.resource_id,sku.sku_code
                FROM bf_resource_sku sku JOIN bf_resource r ON r.id=sku.resource_id
                WHERE sku.id=? AND r.resource_type=? FOR UPDATE
                """, (rs, rowNum) -> new ScenicSkuLocked(rs.getLong("id"), rs.getLong("resource_id"),
                rs.getString("sku_code")), skuId, SCENIC_TICKET);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区票种不存在");
        return rows.get(0);
    }

    private ScenicSkuLocked lockPackageSku(long packageSkuId) {
        ScenicSkuLocked packageSku = lockScenicSku(packageSkuId);
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT p.id FROM bf_resource_package p
                WHERE p.package_code=? FOR UPDATE
                """, Long.class, packageSku.skuCode());
        if (ids.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "套票不存在");
        }
        return packageSku;
    }

    private long packageId(long packageSkuId) {
        return jdbcTemplate.queryForObject("""
                SELECT p.id FROM bf_resource_package p JOIN bf_resource_sku sku ON sku.sku_code=p.package_code
                WHERE sku.id=?
                """, Long.class, packageSkuId);
    }

    private AdminScenicSpotResponse findSpot(long scenicSpotId) {
        return jdbcTemplate.queryForObject("""
                SELECT r.id,r.site_id,s.site_code,r.name,r.category_code,r.status
                FROM bf_resource r JOIN bf_business_site s ON s.id=r.site_id WHERE r.id=?
                """, (rs, rowNum) -> new AdminScenicSpotResponse(rs.getLong("id"), rs.getLong("site_id"),
                rs.getString("site_code"), rs.getString("name"), rs.getString("category_code"),
                rs.getString("status")), scenicSpotId);
    }

    private AdminScenicTicketResponse findTicket(long skuId) {
        List<AdminScenicTicketResponse> rows = jdbcTemplate.query(ticketSelect() + " sku.id=?", ticketMapper(), skuId);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "景区票种不存在");
        return rows.get(0);
    }

    private ScenicPackageResponse findPackageDetail(long packageSkuId) {
        PackageHead head = jdbcTemplate.queryForObject("""
                SELECT sku.id package_sku_id,p.id,p.package_code,p.name,p.description,p.price_cent,p.status
                FROM bf_resource_package p JOIN bf_resource_sku sku ON sku.sku_code=p.package_code
                WHERE sku.id=?
                """, (rs, rowNum) -> new PackageHead(rs.getLong("package_sku_id"), rs.getLong("id"),
                rs.getString("package_code"), rs.getString("name"), rs.getString("description"),
                rs.getLong("price_cent"), rs.getString("status")), packageSkuId);
        List<ScenicPackageResponse.Component> components = jdbcTemplate.query("""
                SELECT sku.id,sku.sku_code,sku.name sku_name,r.name resource_name,r.resource_type,item.quantity
                FROM bf_resource_package_item item
                JOIN bf_resource_sku sku ON sku.id=item.sku_id
                JOIN bf_resource r ON r.id=sku.resource_id
                WHERE item.package_id=? ORDER BY sku.id
                """, (rs, rowNum) -> new ScenicPackageResponse.Component(rs.getLong("id"),
                rs.getString("sku_code"), rs.getString("sku_name"), rs.getString("resource_name"),
                rs.getString("resource_type"), rs.getInt("quantity")), head.packageId());
        return new ScenicPackageResponse(head.packageSkuId(), head.packageId(), head.packageCode(), head.name(),
                head.description(), head.priceCent(), "CNY", head.status(), components);
    }

    private String ticketSelect() {
        return """
                SELECT sku.id sku_id,sku.resource_id,sku.sku_code,sku.name,sku.price_cent,sku.status,
                       JSON_UNQUOTE(JSON_EXTRACT(sku.attributes, '$.ticketType')) ticket_type,
                       p.audience_rule,p.usage_rule,p.refund_rule,p.entry_notice,p.valid_days,p.max_per_order,
                       p.real_name_required,p.id_card_required
                FROM bf_resource_sku sku JOIN bf_resource r ON r.id=sku.resource_id
                LEFT JOIN bf_ticket_profile p ON p.sku_id=sku.id
                WHERE r.resource_type='SCENIC_TICKET' AND
                """;
    }

    private org.springframework.jdbc.core.RowMapper<AdminScenicTicketResponse> ticketMapper() {
        return (rs, rowNum) -> new AdminScenicTicketResponse(rs.getLong("sku_id"), rs.getLong("resource_id"),
                rs.getString("sku_code"), rs.getString("name"), rs.getString("ticket_type"),
                rs.getLong("price_cent"), "CNY", rs.getString("status"), rs.getString("audience_rule"),
                rs.getString("usage_rule"), rs.getString("refund_rule"), rs.getString("entry_notice"),
                rs.getObject("valid_days", Integer.class) == null ? 1 : rs.getInt("valid_days"),
                rs.getObject("max_per_order", Integer.class), rs.getBoolean("real_name_required"),
                rs.getBoolean("id_card_required"));
    }

    private ScenicInventorySetupResponse findInventory(long inventoryId) {
        return jdbcTemplate.queryForObject("""
                SELECT id,sku_id,business_date,time_slot,total_quantity,available_quantity,price_cent
                FROM bf_inventory WHERE id=?
                """, (rs, rowNum) -> {
            int totalQuantity = rs.getInt("total_quantity");
            int availableQuantity = rs.getInt("available_quantity");
            return new ScenicInventorySetupResponse(rs.getLong("id"), rs.getLong("sku_id"),
                    rs.getObject("business_date", LocalDate.class), rs.getString("time_slot"), totalQuantity,
                    totalQuantity - availableQuantity, availableQuantity, rs.getLong("price_cent"));
        }, inventoryId);
    }

    private long ensureOperator(AuthenticatedPrincipal principal) {
        jdbcTemplate.update("""
                INSERT INTO bf_user(wechat_openid,nickname,status) VALUES (?,?,'ACTIVE')
                ON DUPLICATE KEY UPDATE nickname=VALUES(nickname),status='ACTIVE'
                """, principal.databaseOpenId(), principal.displayName());
        return jdbcTemplate.queryForObject("SELECT id FROM bf_user WHERE wechat_openid=?", Long.class,
                principal.databaseOpenId());
    }

    private void audit(long operatorId, String action, String targetType, long targetId,
                       String jsonExpression, Object... values) {
        jdbcTemplate.update("INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail) VALUES (?,?,?,?,"
                + jsonExpression + ")", merge(new Object[]{operatorId, action, targetType, String.valueOf(targetId)}, values));
    }

    private Object[] merge(Object[] prefix, Object[] values) {
        Object[] result = new Object[prefix.length + values.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(values, 0, result, prefix.length, values.length);
        return result;
    }

    private void validateCreateSpot(AdminScenicSpotCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.siteCode())
                || !request.siteCode().trim().matches("[A-Za-z0-9_]{3,50}")) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "siteCode只能使用3—50位字母、数字或下划线");
        }
        validateSpotFields(request.name(), request.categoryCode(), request.coverUrl(), request.address(),
                request.longitude(), request.latitude(), request.servicePhone(), request.description(),
                request.introduction(), request.openingHours(), request.recommendedDurationMinutes(), request.status());
    }

    private void validateUpdateSpot(AdminScenicSpotUpdateRequest request) {
        if (request == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "请求体不能为空");
        validateSpotFields(request.name(), request.categoryCode(), request.coverUrl(), request.address(),
                request.longitude(), request.latitude(), request.servicePhone(), request.description(),
                request.introduction(), request.openingHours(), request.recommendedDurationMinutes(), request.status());
    }

    private void validateSpotFields(String name, String categoryCode, String coverUrl, String address,
                                    BigDecimal longitude, BigDecimal latitude, String servicePhone,
                                    String description, String introduction, String openingHours,
                                    Integer recommendedDurationMinutes, String status) {
        requireText(name, "景区名称", 200);
        validateOptionalText(categoryCode, "categoryCode", 50);
        validateOptionalText(coverUrl, "coverUrl", 512);
        validateOptionalText(address, "address", 255);
        validateOptionalText(servicePhone, "servicePhone", 32);
        validateOptionalText(description, "description", 65535);
        validateOptionalText(introduction, "introduction", 65535);
        validateOptionalText(openingHours, "openingHours", 100);
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "longitude范围应为-180到180");
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "latitude范围应为-90到90");
        }
        if (recommendedDurationMinutes != null && (recommendedDurationMinutes < 1 || recommendedDurationMinutes > 1440)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "recommendedDurationMinutes范围为1—1440");
        }
        normalizeStatus(status);
    }

    private void validateCreateTicket(AdminScenicTicketCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.skuCode())
                || !request.skuCode().trim().matches("[A-Za-z0-9_]{3,64}")) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "skuCode只能使用3—64位字母、数字或下划线");
        }
        validateTicketFields(request.name(), request.ticketType(), request.priceCent(), request.status(),
                request.audienceRule(), request.usageRule(), request.refundRule(), request.entryNotice(),
                request.validDays(), request.maxPerOrder(), request.realNameRequired(), request.idCardRequired());
    }

    private void validateUpdateTicket(AdminScenicTicketUpdateRequest request) {
        if (request == null) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "请求体不能为空");
        validateTicketFields(request.name(), request.ticketType(), request.priceCent(), request.status(),
                request.audienceRule(), request.usageRule(), request.refundRule(), request.entryNotice(),
                request.validDays(), request.maxPerOrder(), request.realNameRequired(), request.idCardRequired());
    }

    private void validateTicketFields(String name, String ticketType, Long priceCent, String status,
                                      String audienceRule, String usageRule, String refundRule, String entryNotice,
                                      Integer validDays, Integer maxPerOrder, Boolean realNameRequired,
                                      Boolean idCardRequired) {
        requireText(name, "票种名称", 200);
        normalizeTicketType(ticketType);
        if (priceCent == null || priceCent < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "priceCent必须是大于等于0的分金额");
        }
        normalizeStatus(status);
        validateOptionalText(audienceRule, "audienceRule", 500);
        requireText(usageRule, "usageRule", 65535);
        validateOptionalText(refundRule, "refundRule", 65535);
        validateOptionalText(entryNotice, "entryNotice", 65535);
        if (validDays == null || validDays < 1 || validDays > 365) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "validDays范围为1—365");
        }
        if (maxPerOrder != null && (maxPerOrder < 1 || maxPerOrder > 99)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "maxPerOrder范围为1—99");
        }
        if (realNameRequired == null || idCardRequired == null || (idCardRequired && !realNameRequired)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "身份证核验必须同时启用实名购票");
        }
    }

    private void validateInventorySetup(LocalDate businessDate, String timeSlot, String idempotencyKey,
                                        ScenicInventorySetupRequest request) {
        if (businessDate == null || businessDate.isBefore(LocalDate.now())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "预约日期不能早于今天");
        }
        if (businessDate.isAfter(LocalDate.now().plusYears(2))) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "预约日期最多提前两年配置");
        }
        normalizeSlot(timeSlot);
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.trim().length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "Idempotency-Key不能为空且最长64字符");
        }
        if (request == null || request.totalQuantity() == null || request.totalQuantity() < 0
                || request.totalQuantity() > 1_000_000 || request.priceCent() == null || request.priceCent() < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                    "totalQuantity范围为0—1000000，priceCent必须是大于等于0的分金额");
        }
    }

    private void validatePackageComponents(long packageSkuId, PackageComponentUpdateRequest request) {
        if (request == null || request.components() == null || request.components().size() < 2
                || request.components().size() > 10) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "套票组件数量范围为2—10");
        }
        Set<Long> uniqueSkuIds = new java.util.HashSet<>();
        for (PackageComponentRequest component : request.components()) {
            if (component == null || component.skuId() == null || component.skuId() <= 0
                    || component.quantity() == null || component.quantity() < 1 || component.quantity() > 10) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "组件SKU和数量不合法，数量范围为1—10");
            }
            if (component.skuId() == packageSkuId || !uniqueSkuIds.add(component.skuId())) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "套票组件不能包含自身或重复SKU");
            }
        }
    }

    private PackageComponent requirePackageComponent(long packageSkuId, PackageComponentRequest request) {
        List<PackageComponent> rows = jdbcTemplate.query("""
                SELECT sku.id,sku.sku_code,sku.name,r.name resource_name,r.resource_type,
                       JSON_UNQUOTE(JSON_EXTRACT(sku.attributes, '$.ticketType')) ticket_type
                FROM bf_resource_sku sku JOIN bf_resource r ON r.id=sku.resource_id
                WHERE sku.id=? AND sku.status='ACTIVE' AND r.status='ACTIVE' AND r.resource_type='SCENIC_TICKET'
                """, (rs, rowNum) -> new PackageComponent(rs.getLong("id"), rs.getString("sku_code"),
                rs.getString("name"), rs.getString("resource_name"), rs.getString("resource_type"),
                rs.getString("ticket_type"), request.quantity()), request.skuId());
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "套票组件SKU不存在、未上架或不属于景区票务");
        }
        PackageComponent component = rows.get(0);
        if ("PACKAGE".equals(component.ticketType())) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "套票不能嵌套其他套票");
        }
        return component;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "status不能为空");
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "status仅支持DRAFT、ACTIVE、INACTIVE");
        }
        return normalized;
    }

    private String normalizeTicketType(String ticketType) {
        if (!StringUtils.hasText(ticketType)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "ticketType不能为空");
        String normalized = ticketType.trim().toUpperCase(Locale.ROOT);
        if (!TICKET_TYPES.contains(normalized)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST,
                    "ticketType仅支持ADULT、CHILD、PACKAGE、SERVICE、OTHER");
        }
        return normalized;
    }

    private String normalizeSlot(String timeSlot) {
        String normalized = timeSlot == null ? "" : timeSlot.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "timeSlot最长64字符");
        }
        return normalized;
    }

    private void requireText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, fieldName + "不能为空且最长" + maxLength + "字符");
        }
    }

    private void validateOptionalText(String value, String fieldName, int maxLength) {
        if (value != null && value.trim().length() > maxLength) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, fieldName + "最长" + maxLength + "字符");
        }
    }

    private String nullableText(String value, int maxLength) {
        validateOptionalText(value, "字段", maxLength);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long requireGeneratedId(KeyHolder keys, String message) {
        if (keys.getKey() == null) throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, message);
        return keys.getKey().longValue();
    }

    private record ScenicSpotLocked(long id, long siteId, String siteCode) {}
    private record ScenicSkuLocked(long id, long scenicSpotId, String skuCode) {}
    private record InventoryLocked(long id, int totalQuantity, int availableQuantity) {}
    private record PackageHead(long packageSkuId, long packageId, String packageCode, String name,
                               String description, long priceCent, String status) {}
    private record PackageComponent(long skuId, String skuCode, String skuName, String resourceName,
                                    String resourceType, String ticketType, int quantity) {}
}
