package com.beifanghui.backend.user.api;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserDetailResponse(
        long id,
        String nickname,
        String realName,
        String mobile,
        String avatarUrl,
        int gender,
        String status,
        String levelCode,
        int points,
        long balanceCent,
        int growthValue,
        long orderCount,
        long cumulativeSpendCent,
        long refundCount,
        long verificationCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        List<OrderItem> recentOrders,
        List<RefundItem> recentRefunds,
        List<VerificationItem> recentVerifications) {

    public record OrderItem(long id, String orderNo, String status, long payableAmountCent,
                            long paidAmountCent, LocalDateTime createdAt) {
    }

    public record RefundItem(long id, String refundNo, long orderId, String status,
                             long amountCent, String reason, LocalDateTime createdAt) {
    }

    public record VerificationItem(long id, long orderId, String resourceName, String status,
                                   LocalDateTime verifiedAt, LocalDateTime createdAt) {
    }
}
