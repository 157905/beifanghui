package com.beifanghui.backend.verification.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import com.beifanghui.backend.verification.application.VerificationTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffVerificationController {
    private final VerificationTicketService service;
    public StaffVerificationController(VerificationTicketService service) { this.service = service; }

    @PostMapping({"/api/v1/admin/verifications/consume", "/api/v1/ops/verifications/consume"})
    public ApiResponse<VerificationTicketResponse> consume(@RequestBody ConsumeVerificationRequest body,
                                                            HttpServletRequest request) {
        return ApiResponse.success("核销成功",
                service.consume(CurrentPrincipal.from(request), body == null ? null : body.code()),
                TraceIds.from(request));
    }
}
