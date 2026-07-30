package com.beifanghui.backend.verification.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VerificationTicketResponse(
        long verificationId, long orderId, String orderNo, long orderItemId, int ticketNo,
        String resourceName, String resourceType, String code, String status,
        LocalDate serviceDate, LocalDateTime verifiedAt) {
}
