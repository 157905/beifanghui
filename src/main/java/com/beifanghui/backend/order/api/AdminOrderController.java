package com.beifanghui.backend.order.api;

import com.beifanghui.backend.order.application.AdminOrderQueryService;
import com.beifanghui.backend.order.application.ExpiredOrderService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final AdminOrderQueryService queryService;
    private final ExpiredOrderService expiredOrderService;
    public AdminOrderController(AdminOrderQueryService queryService, ExpiredOrderService expiredOrderService) {
        this.queryService = queryService;
        this.expiredOrderService = expiredOrderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminOrderSummaryResponse>> list(
            @RequestParam(required=false) String status, @RequestParam(required=false) String orderNo,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success(queryService.list(status, orderNo, page, pageSize), TraceIds.from(request));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> detail(@PathVariable long orderId, HttpServletRequest request) {
        return ApiResponse.success(queryService.detail(orderId), TraceIds.from(request));
    }

    @PostMapping("/expire-overdue")
    public ApiResponse<Map<String,Integer>> expireOverdue(HttpServletRequest request) {
        int count = expiredOrderService.expireOverdue();
        return ApiResponse.success("超时订单扫描完成", Map.of("expiredCount", count), TraceIds.from(request));
    }
}
