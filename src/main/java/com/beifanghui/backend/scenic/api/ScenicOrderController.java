package com.beifanghui.backend.scenic.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.scenic.application.ScenicVisitorQueryService;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/app/scenic/orders")
public class ScenicOrderController {
    private final ScenicVisitorQueryService service;
    public ScenicOrderController(ScenicVisitorQueryService service){this.service=service;}

    @GetMapping("/{orderId}/visitors")
    public ApiResponse<List<ScenicVisitorResponse>> visitors(@PathVariable long orderId,HttpServletRequest request){
        return ApiResponse.success("游客信息查询成功",service.list(CurrentPrincipal.from(request),orderId),TraceIds.from(request));
    }
}
