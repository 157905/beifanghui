package com.beifanghui.backend.verification.api;

import com.beifanghui.backend.identity.web.CurrentPrincipal;
import com.beifanghui.backend.shared.api.ApiResponse;
import com.beifanghui.backend.shared.web.TraceIds;
import com.beifanghui.backend.verification.application.VerificationTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/app/orders")
public class AppVerificationController {
    private final VerificationTicketService service;
    public AppVerificationController(VerificationTicketService service) { this.service = service; }

    @GetMapping("/{orderId}/tickets")
    public ApiResponse<List<VerificationTicketResponse>> tickets(@PathVariable long orderId,
                                                                  HttpServletRequest request) {
        return ApiResponse.success("电子票查询成功",
                service.listOwnedTickets(CurrentPrincipal.from(request), orderId), TraceIds.from(request));
    }
}
