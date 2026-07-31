package com.beifanghui.backend.user.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import com.beifanghui.backend.user.application.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String levelCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(keyword, status, levelCode, page, pageSize), TraceIds.from(request));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetailResponse> detail(@PathVariable long userId, HttpServletRequest request) {
        return ApiResponse.success(service.detail(userId), TraceIds.from(request));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminUserDetailResponse> updateStatus(
            @PathVariable long userId,
            @RequestBody AdminUserStatusUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("用户状态更新成功",
                service.updateStatus(CurrentPrincipal.from(request), userId, body), TraceIds.from(request));
    }
}
