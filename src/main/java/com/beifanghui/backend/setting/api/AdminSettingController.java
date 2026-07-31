package com.beifanghui.backend.setting.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.setting.application.AdminSettingService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingController {

    private final AdminSettingService service;

    public AdminSettingController(AdminSettingService service) {
        this.service = service;
    }

    @GetMapping("/platform")
    public ApiResponse<List<PlatformSettingResponse>> platformSettings(HttpServletRequest request) {
        return ApiResponse.success(service.platformSettings(), TraceIds.from(request));
    }

    @PutMapping("/platform/{settingKey}")
    public ApiResponse<PlatformSettingResponse> updatePlatformSetting(
            @PathVariable String settingKey,
            @RequestBody PlatformSettingUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("平台参数更新成功", service.updatePlatformSetting(
                CurrentPrincipal.from(request), settingKey, body == null ? null : body.value()), TraceIds.from(request));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> permissions(HttpServletRequest request) {
        return ApiResponse.success(service.permissions(), TraceIds.from(request));
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> roles(HttpServletRequest request) {
        return ApiResponse.success(service.roles(), TraceIds.from(request));
    }

    @PutMapping("/roles/{roleId}/permissions")
    public ApiResponse<RoleResponse> updateRolePermissions(
            @PathVariable long roleId,
            @RequestBody RolePermissionsUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("角色权限更新成功", service.updateRolePermissions(
                CurrentPrincipal.from(request), roleId, body == null ? null : body.permissionIds()), TraceIds.from(request));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AuditLogResponse>> auditLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(service.auditLogs(keyword, action, page, pageSize), TraceIds.from(request));
    }
}
