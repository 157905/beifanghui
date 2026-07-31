package com.beifanghui.backend.report.api;

import java.time.LocalDate;

public record DailyReportItem(
        LocalDate date,
        long orderCount,
        long paidOrderCount,
        long grossRevenueCent,
        long refundAmountCent,
        long netRevenueCent,
        long newUserCount,
        long verificationCount) {
}
