package com.beifanghui.backend.setting.application;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.setting.api.*;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.error.BusinessException;
import com.beifanghui.backend.shared.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminSettingService {

    private final JdbcTemplate jdbcTemplate;

    public AdminSettingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<PlatformSettingResponse> platformSettings() {
        return jdbcTemplate.query("""
                SELECT setting_key,setting_value,value_type,is_public,description,updated_by,updated_at
                FROM bf_platform_setting ORDER BY setting_key
                """, (rs, rowNum) -> new PlatformSettingResponse(
                rs.getString("setting_key"), rs.getString("setting_value"), rs.getString("value_type"),
                rs.getBoolean("is_public"), rs.getString("description"), rs.getObject("updated_by", Long.class),
                rs.getObject("updated_at", LocalDateTime.class)));
    }

    @Transactional
    public PlatformSettingResponse updatePlatformSetting(
            AuthenticatedPrincipal principal, String settingKey, String requestedValue) {
        if (!StringUtils.hasText(settingKey) || settingKey.length() > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "settingKey不正确");
        }
        List<SettingType> rows = jdbcTemplate.query("""
                SELECT value_type FROM bf_platform_setting WHERE setting_key=? FOR UPDATE
                """, (rs, rowNum) -> new SettingType(rs.getString("value_type")), settingKey);
        if (rows.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "平台参数不存在");
        String value = validateSettingValue(rows.get(0).valueType(), requestedValue);
        long operatorId = ensureOperator(principal);
        jdbcTemplate.update("UPDATE bf_platform_setting SET setting_value=?,updated_by=? WHERE setting_key=?",
                value, operatorId, settingKey);
        audit(operatorId, "PLATFORM_SETTING_UPDATE", "PLATFORM_SETTING", settingKey,
                "JSON_OBJECT('value',?)", value);
        return findSetting(settingKey);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissions() {
        return jdbcTemplate.query("""
                SELECT id,code,name,permission_type FROM bf_permission ORDER BY code
                """, (rs, rowNum) -> new PermissionResponse(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("permission_type")));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return jdbcTemplate.query("SELECT id,code,name FROM bf_role ORDER BY id", (rs, rowNum) -> {
            long roleId = rs.getLong("id");
            List<PermissionResponse> permissions = rolePermissions(roleId);
            long userCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bf_user_role WHERE role_id=?", Long.class, roleId);
            return new RoleResponse(roleId, rs.getString("code"), rs.getString("name"), userCount,
                    permissions.stream().map(PermissionResponse::id).toList(),
                    permissions.stream().map(PermissionResponse::code).toList());
        });
    }

    @Transactional
    public RoleResponse updateRolePermissions(
            AuthenticatedPrincipal principal, long roleId, List<Long> requestedPermissionIds) {
        List<String> codes = jdbcTemplate.queryForList("SELECT code FROM bf_role WHERE id=? FOR UPDATE",
                String.class, roleId);
        if (codes.isEmpty()) throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        Set<Long> permissionIds = requestedPermissionIds == null
                ? Set.of() : new LinkedHashSet<>(requestedPermissionIds);
        if (permissionIds.size() > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "权限数量不能超过100");
        }
        if (!permissionIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(permissionIds.size(), "?"));
            long existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bf_permission WHERE id IN (" + placeholders + ")",
                    Long.class, permissionIds.toArray());
            if (existing != permissionIds.size()) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "包含不存在的权限");
            }
        }
        if ("ADMIN".equals(codes.get(0))) {
            List<Long> settingsPermission = jdbcTemplate.queryForList(
                    "SELECT id FROM bf_permission WHERE code='settings.manage'", Long.class);
            if (!settingsPermission.isEmpty() && !permissionIds.contains(settingsPermission.get(0))) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "系统管理员必须保留系统设置权限");
            }
        }
        jdbcTemplate.update("DELETE FROM bf_role_permission WHERE role_id=?", roleId);
        for (Long permissionId : permissionIds) {
            jdbcTemplate.update("INSERT INTO bf_role_permission(role_id,permission_id) VALUES (?,?)",
                    roleId, permissionId);
        }
        long operatorId = ensureOperator(principal);
        audit(operatorId, "ROLE_PERMISSION_UPDATE", "ROLE", String.valueOf(roleId),
                "JSON_OBJECT('permissionCount',?)", permissionIds.size());
        return roles().stream().filter(role -> role.id() == roleId).findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在"));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLogs(
            String keyword, String action, int page, int pageSize) {
        validatePage(page, pageSize);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (u.nickname LIKE ? OR a.target_type LIKE ? OR a.target_id LIKE ? OR a.trace_id LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value); args.add(value); args.add(value);
        }
        if (StringUtils.hasText(action)) {
            where.append(" AND a.action LIKE ?");
            args.add("%" + action.trim() + "%");
        }
        long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM bf_audit_log a LEFT JOIN bf_user u ON u.id=a.operator_id
                """ + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<AuditLogResponse> items = jdbcTemplate.query("""
                SELECT a.id,a.operator_id,u.nickname operator_name,a.action,a.target_type,a.target_id,
                       CAST(a.detail AS CHAR) detail,a.ip_address,a.trace_id,a.created_at
                FROM bf_audit_log a LEFT JOIN bf_user u ON u.id=a.operator_id
                """ + where + " ORDER BY a.id DESC LIMIT ? OFFSET ?", (rs, rowNum) -> new AuditLogResponse(
                rs.getLong("id"), rs.getObject("operator_id", Long.class), rs.getString("operator_name"),
                rs.getString("action"), rs.getString("target_type"), rs.getString("target_id"),
                rs.getString("detail"), rs.getString("ip_address"), rs.getString("trace_id"),
                rs.getObject("created_at", LocalDateTime.class)), pageArgs.toArray());
        int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, total, totalPages);
    }

    private PlatformSettingResponse findSetting(String key) {
        return jdbcTemplate.queryForObject("""
                SELECT setting_key,setting_value,value_type,is_public,description,updated_by,updated_at
                FROM bf_platform_setting WHERE setting_key=?
                """, (rs, rowNum) -> new PlatformSettingResponse(
                rs.getString("setting_key"), rs.getString("setting_value"), rs.getString("value_type"),
                rs.getBoolean("is_public"), rs.getString("description"), rs.getObject("updated_by", Long.class),
                rs.getObject("updated_at", LocalDateTime.class)), key);
    }

    private List<PermissionResponse> rolePermissions(long roleId) {
        return jdbcTemplate.query("""
                SELECT p.id,p.code,p.name,p.permission_type FROM bf_permission p
                JOIN bf_role_permission rp ON rp.permission_id=p.id WHERE rp.role_id=? ORDER BY p.code
                """, (rs, rowNum) -> new PermissionResponse(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("permission_type")), roleId);
    }

    private String validateSettingValue(String type, String value) {
        if (value == null || value.length() > 10000) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "参数值不能为空且最长10000字符");
        }
        String result = value.trim();
        try {
            return switch (type) {
                case "BOOLEAN" -> {
                    if (!"true".equalsIgnoreCase(result) && !"false".equalsIgnoreCase(result)) {
                        throw new IllegalArgumentException();
                    }
                    yield result.toLowerCase();
                }
                case "INTEGER" -> String.valueOf(Long.parseLong(result));
                case "JSON" -> {
                    Integer valid = jdbcTemplate.queryForObject("SELECT JSON_VALID(?)", Integer.class, result);
                    if (valid == null || valid != 1) throw new IllegalArgumentException();
                    yield result;
                }
                default -> result;
            };
        } catch (Exception exception) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "参数值不符合" + type + "类型");
        }
    }

    private long ensureOperator(AuthenticatedPrincipal principal) {
        String openId = principal.databaseOpenId();
        jdbcTemplate.update("""
                INSERT INTO bf_user(wechat_openid,nickname,status) VALUES (?,?,'ACTIVE')
                ON DUPLICATE KEY UPDATE nickname=VALUES(nickname)
                """, openId, principal.displayName());
        return jdbcTemplate.queryForObject("SELECT id FROM bf_user WHERE wechat_openid=?", Long.class, openId);
    }

    private void audit(long operatorId, String action, String targetType, String targetId,
                       String detailExpression, Object detailValue) {
        String sql = "INSERT INTO bf_audit_log(operator_id,action,target_type,target_id,detail) "
                + "VALUES (?,?,?,?," + detailExpression + ")";
        jdbcTemplate.update(sql, operatorId, action, targetType, targetId, detailValue);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "page最小为1，pageSize范围为1—100");
        }
    }

    private record SettingType(String valueType) {
    }
}
