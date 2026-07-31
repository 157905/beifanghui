package com.beifanghui.backend.report.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record ReportOverviewResponse(
        LocalDate startDate,
        LocalDate endDate,
        long orderCount,
        long paidOrderCount,
        long grossRevenueCent,
        long refundAmountCent,
        long netRevenueCent,
        long newUserCount,
        long verificationCount,
        Map<String, Long> orderStatusCounts,
        LocalDateTime generatedAt) {
}
