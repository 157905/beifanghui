package com.beifanghui.backend.catalog.api;

import com.beifanghui.backend.catalog.application.AdminCatalogService;
import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private final AdminCatalogService service;
    public AdminCatalogController(AdminCatalogService service) { this.service=service; }

    @PatchMapping("/resources/{resourceId}/status")
    public ApiResponse<AdminResourceResponse> updateStatus(@PathVariable long resourceId,
                                                            @RequestBody ResourceStatusUpdateRequest body,
                                                            HttpServletRequest request) {
        return ApiResponse.success("资源状态更新成功",service.updateStatus(CurrentPrincipal.from(request),resourceId,
                body==null?null:body.status()),TraceIds.from(request));
    }

    @PostMapping("/inventories/{inventoryId}/adjust")
    public ApiResponse<InventoryAdjustmentResponse> adjust(@PathVariable long inventoryId,
                                                            @RequestHeader("Idempotency-Key") String key,
                                                            @RequestBody InventoryAdjustmentRequest body,
                                                            HttpServletRequest request) {
        return ApiResponse.success("库存调整成功",service.adjustInventory(CurrentPrincipal.from(request),inventoryId,key,body),
                TraceIds.from(request));
    }
}
