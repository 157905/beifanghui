package com.beifanghui.backend.catalog.api;

import com.beifanghui.backend.catalog.application.CatalogQueryService;
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
@RequestMapping("/api/v1/app")
public class CatalogController {
    private final CatalogQueryService catalogQueryService;

    public CatalogController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping("/resources")
    public ApiResponse<PageResponse<ResourceSummaryResponse>> resources(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(catalogQueryService.list(type, page, pageSize), TraceIds.from(request));
    }

    @GetMapping("/resources/{resourceId}")
    public ApiResponse<ResourceDetailResponse> resourceDetail(@PathVariable Long resourceId,
                                                               HttpServletRequest request) {
        return ApiResponse.success(catalogQueryService.detail(resourceId), TraceIds.from(request));
    }

    @GetMapping("/resource-skus/{skuId}/availability")
    public ApiResponse<AvailabilityResponse> availability(
            @PathVariable Long skuId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "") String timeSlot,
            HttpServletRequest request) {
        return ApiResponse.success(catalogQueryService.availability(skuId, date, timeSlot), TraceIds.from(request));
    }
}
