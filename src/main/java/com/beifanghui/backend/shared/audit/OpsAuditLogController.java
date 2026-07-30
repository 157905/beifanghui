package com.beifanghui.backend.shared.audit;

import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.api.PageResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ops/audit-logs")
public class OpsAuditLogController {
    private final AuditLogQueryService service;
    public OpsAuditLogController(AuditLogQueryService service){this.service=service;}

    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> list(
            @RequestParam(required=false) String action,@RequestParam(required=false) String targetType,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,
            HttpServletRequest request){
        return ApiResponse.success(service.list(action,targetType,page,pageSize),TraceIds.from(request));
    }
}
