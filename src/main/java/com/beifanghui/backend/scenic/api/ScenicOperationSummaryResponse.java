package com.beifanghui.backend.scenic.api;

import java.time.LocalDate;

/** 景区运营概览；金额单位为分，核销率为百分比。 */
public record ScenicOperationSummaryResponse(
        long scenicSpotId,
        String scenicSpotName,
        LocalDate startDate,
        LocalDate endDate,
        long paidOrderCount,
        long soldTicketCount,
        long salesAmountCent,
        String currency,
        long issuedTicketCount,
        long verifiedTicketCount,
        double verificationRate) {
}
