package com.beifanghui.backend.scenic.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 单张已核销电子票的运营记录，不返回核销码或游客敏感信息。 */
public record ScenicVerificationRecordResponse(
        long verificationId,
        long orderId,
        String orderNo,
        long orderItemId,
        int ticketNo,
        String ticketName,
        String resourceName,
        LocalDate serviceDate,
        LocalDateTime verifiedAt,
        Long verifierUserId,
        String verifierName,
        String verificationChannel) {
}
