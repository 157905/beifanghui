package com.beifanghui.backend.scenic.api;

import com.beifanghui.backend.scenic.application.ScenicSpotQueryService;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/app/scenic-spots")
public class ScenicSpotController {
    private final ScenicSpotQueryService queryService;

    public ScenicSpotController(ScenicSpotQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ScenicSpotSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(queryService.list(keyword, page, pageSize), TraceIds.from(request));
    }

    @GetMapping("/{scenicSpotId}")
    public ApiResponse<ScenicSpotDetailResponse> detail(
            @PathVariable long scenicSpotId,
            HttpServletRequest request) {
        return ApiResponse.success(queryService.detail(scenicSpotId), TraceIds.from(request));
    }

    @GetMapping("/{scenicSpotId}/tickets")
    public ApiResponse<List<ScenicTicketResponse>> tickets(
            @PathVariable long scenicSpotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            HttpServletRequest request) {
        return ApiResponse.success(queryService.tickets(scenicSpotId, serviceDate), TraceIds.from(request));
    }
}
