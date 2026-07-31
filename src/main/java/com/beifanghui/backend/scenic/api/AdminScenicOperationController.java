package com.beifanghui.backend.scenic.api;

import com.beifanghui.backend.scenic.application.ScenicOperationQueryService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/scenic-spots/{scenicSpotId}/operations")
public class AdminScenicOperationController {
    private final ScenicOperationQueryService service;

    public AdminScenicOperationController(ScenicOperationQueryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<ScenicOperationSummaryResponse> summary(
            @PathVariable long scenicSpotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) {
        return ApiResponse.success(service.summary(scenicSpotId, startDate, endDate), TraceIds.from(request));
    }

    @GetMapping("/verification-records")
    public ApiResponse<PageResponse<ScenicVerificationRecordResponse>> verificationRecords(
            @PathVariable long scenicSpotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(service.verificationRecords(scenicSpotId, startDate, endDate, page, pageSize),
                TraceIds.from(request));
    }
}
