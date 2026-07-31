package com.beifanghui.backend.report.api;

import com.beifanghui.backend.report.application.AdminReportService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final AdminReportService service;

    public AdminReportController(AdminReportService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<ReportOverviewResponse> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) {
        return ApiResponse.success(service.overview(startDate, endDate), TraceIds.from(request));
    }

    @GetMapping("/daily")
    public ApiResponse<List<DailyReportItem>> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) {
        return ApiResponse.success(service.daily(startDate, endDate), TraceIds.from(request));
    }

    @GetMapping("/resource-sales")
    public ApiResponse<List<ResourceSalesReportItem>> resourceSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        return ApiResponse.success(service.resourceSales(startDate, endDate, limit), TraceIds.from(request));
    }
}
