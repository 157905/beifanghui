package com.beifanghui.backend.order.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.order.application.OrderApplicationService;
import com.beifanghui.backend.order.application.OrderTimelineService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app/orders")
public class OrderController {
    private final OrderApplicationService orderService;
    private final OrderTimelineService timelineService;

    public OrderController(OrderApplicationService orderService, OrderTimelineService timelineService) {
        this.orderService = orderService;
        this.timelineService = timelineService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @RequestBody CreateOrderRequest body,
                                             HttpServletRequest request) {
        return ApiResponse.success("订单请求处理成功",
                orderService.create(CurrentPrincipal.from(request), idempotencyKey, body), TraceIds.from(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize,
                                                         HttpServletRequest request) {
        return ApiResponse.success(orderService.list(CurrentPrincipal.from(request), page, pageSize), TraceIds.from(request));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> detail(@PathVariable long orderId, HttpServletRequest request) {
        return ApiResponse.success(orderService.detail(CurrentPrincipal.from(request), orderId), TraceIds.from(request));
    }

    @GetMapping("/{orderId}/timeline")
    public ApiResponse<OrderTimelineResponse> timeline(@PathVariable long orderId, HttpServletRequest request) {
        return ApiResponse.success(timelineService.timeline(CurrentPrincipal.from(request), orderId), TraceIds.from(request));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable long orderId, HttpServletRequest request) {
        return ApiResponse.success("订单取消成功", orderService.cancel(CurrentPrincipal.from(request), orderId), TraceIds.from(request));
    }
}
