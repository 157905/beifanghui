package com.beifanghui.backend.dashboard.api;

import java.time.LocalDateTime;

/** 管理端首页汇总数据；金额统一使用分。 */
public record AdminDashboardSummaryResponse(
        long todayOrderCount,
        long pendingPaymentOrderCount,
        long pendingVerificationCount,
        long activeResourceCount,
        long activeSkuCount,
        long pendingRefundCount,
        long todayRevenueCent,
        String currency,
        LocalDateTime generatedAt) {
}
