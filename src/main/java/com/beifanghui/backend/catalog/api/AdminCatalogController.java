package com.beifanghui.backend.catalog.api;

import com.beifanghui.backend.catalog.application.AdminCatalogService;
import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private final AdminCatalogService service;
    public AdminCatalogController(AdminCatalogService service) { this.service=service; }

    @GetMapping("/resources")
    public ApiResponse<PageResponse<AdminResourceListItem>> listResources(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(service.listResources(type, status, keyword, page, pageSize), TraceIds.from(request));
    }

    @PostMapping("/resources")
    public ApiResponse<AdminResourceListItem> createResource(@RequestBody AdminResourceUpsertRequest body,
                                                               HttpServletRequest request) {
        return ApiResponse.success("资源创建成功", service.createResource(CurrentPrincipal.from(request), body), TraceIds.from(request));
    }

    @PutMapping("/resources/{resourceId}")
    public ApiResponse<AdminResourceListItem> updateResource(@PathVariable long resourceId,
                                                               @RequestBody AdminResourceUpsertRequest body,
                                                               HttpServletRequest request) {
        return ApiResponse.success("资源更新成功", service.updateResource(CurrentPrincipal.from(request), resourceId, body), TraceIds.from(request));
    }

    @GetMapping("/resources/{resourceId}/inventories")
    public ApiResponse<java.util.List<AdminInventoryResponse>> listInventories(@PathVariable long resourceId,
                                                                                 HttpServletRequest request) {
        return ApiResponse.success(service.listInventories(resourceId), TraceIds.from(request));
    }

    @GetMapping("/resources/{resourceId}/skus")
    public ApiResponse<java.util.List<AdminSkuResponse>> listSkus(@PathVariable long resourceId,
                                                                    HttpServletRequest request) {
        return ApiResponse.success(service.listSkus(resourceId), TraceIds.from(request));
    }

    @PostMapping("/resources/{resourceId}/skus")
    public ApiResponse<AdminSkuResponse> createSku(@PathVariable long resourceId,
                                                    @RequestBody AdminSkuUpsertRequest body,
                                                    HttpServletRequest request) {
        return ApiResponse.success("SKU 创建成功", service.createSku(CurrentPrincipal.from(request), resourceId, body), TraceIds.from(request));
    }

    @PutMapping("/resources/{resourceId}/skus/{skuId}")
    public ApiResponse<AdminSkuResponse> updateSku(@PathVariable long resourceId, @PathVariable long skuId,
                                                    @RequestBody AdminSkuUpsertRequest body,
                                                    HttpServletRequest request) {
        return ApiResponse.success("SKU 更新成功", service.updateSku(CurrentPrincipal.from(request), resourceId, skuId, body), TraceIds.from(request));
    }

    @PostMapping("/resources/{resourceId}/inventories")
    public ApiResponse<AdminInventoryResponse> createInventory(@PathVariable long resourceId,
                                                                @RequestBody InventorySetupRequest body,
                                                                HttpServletRequest request) {
        return ApiResponse.success("库存初始化成功", service.createInventory(CurrentPrincipal.from(request), resourceId, body), TraceIds.from(request));
    }

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
