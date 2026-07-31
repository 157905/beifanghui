package com.beifanghui.backend.dashboard.api;

import com.beifanghui.backend.dashboard.application.AdminDashboardQueryService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {
    private final AdminDashboardQueryService queryService;

    public AdminDashboardController(AdminDashboardQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/summary")
    public ApiResponse<AdminDashboardSummaryResponse> summary(HttpServletRequest request) {
        return ApiResponse.success(queryService.summary(), TraceIds.from(request));
    }
}
