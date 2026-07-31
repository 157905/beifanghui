package com.beifanghui.backend.scenic.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.scenic.application.AdminScenicManagementService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminScenicManagementController {
    private final AdminScenicManagementService service;

    public AdminScenicManagementController(AdminScenicManagementService service) {
        this.service = service;
    }

    @PostMapping("/scenic-spots")
    public ApiResponse<AdminScenicSpotResponse> createScenicSpot(
            @RequestBody AdminScenicSpotCreateRequest body, HttpServletRequest request) {
        return ApiResponse.success("景区创建成功", service.createScenicSpot(CurrentPrincipal.from(request), body),
                TraceIds.from(request));
    }

    @PutMapping("/scenic-spots/{scenicSpotId}")
    public ApiResponse<AdminScenicSpotResponse> updateScenicSpot(
            @PathVariable long scenicSpotId, @RequestBody AdminScenicSpotUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("景区更新成功", service.updateScenicSpot(
                CurrentPrincipal.from(request), scenicSpotId, body), TraceIds.from(request));
    }

    @PostMapping("/scenic-spots/{scenicSpotId}/tickets")
    public ApiResponse<AdminScenicTicketResponse> createTicket(
            @PathVariable long scenicSpotId, @RequestBody AdminScenicTicketCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("景区票种创建成功", service.createTicket(
                CurrentPrincipal.from(request), scenicSpotId, body), TraceIds.from(request));
    }

    @GetMapping("/scenic-spots/{scenicSpotId}/tickets")
    public ApiResponse<List<AdminScenicTicketResponse>> tickets(
            @PathVariable long scenicSpotId, HttpServletRequest request) {
        return ApiResponse.success(service.listTickets(CurrentPrincipal.from(request), scenicSpotId),
                TraceIds.from(request));
    }

    @PutMapping("/scenic-tickets/{skuId}")
    public ApiResponse<AdminScenicTicketResponse> updateTicket(
            @PathVariable long skuId, @RequestBody AdminScenicTicketUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("景区票种更新成功", service.updateTicket(
                CurrentPrincipal.from(request), skuId, body), TraceIds.from(request));
    }

    @GetMapping("/scenic-packages/{packageSkuId}/components")
    public ApiResponse<ScenicPackageResponse> packageComponents(
            @PathVariable long packageSkuId, HttpServletRequest request) {
        return ApiResponse.success(service.packageDetail(CurrentPrincipal.from(request), packageSkuId),
                TraceIds.from(request));
    }

    @PutMapping("/scenic-packages/{packageSkuId}/components")
    public ApiResponse<ScenicPackageResponse> replacePackageComponents(
            @PathVariable long packageSkuId, @RequestBody PackageComponentUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("套票组件更新成功", service.replacePackageComponents(
                CurrentPrincipal.from(request), packageSkuId, body), TraceIds.from(request));
    }

    @PutMapping("/scenic-tickets/{skuId}/inventories/{businessDate}")
    public ApiResponse<ScenicInventorySetupResponse> setupInventory(
            @PathVariable long skuId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(defaultValue = "") String timeSlot,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ScenicInventorySetupRequest body,
            HttpServletRequest request) {
        return ApiResponse.success("景区预约时段库存设置成功", service.setupInventory(
                CurrentPrincipal.from(request), skuId, businessDate, timeSlot, idempotencyKey, body),
                TraceIds.from(request));
    }
}
