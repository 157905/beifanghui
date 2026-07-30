package com.beifanghui.backend.order.domain;

import java.util.Set;

public final class OrderStatePolicy {
    private static final Set<String> REFUNDABLE = Set.of("PAID", "READY");
    private static final Set<String> VERIFIABLE = Set.of("PAID", "READY");

    private OrderStatePolicy() {}

    public static boolean canPay(String status) {
        return "PENDING_PAYMENT".equals(status);
    }

    public static boolean canRefund(String status) {
        return REFUNDABLE.contains(status);
    }

    public static boolean canVerify(String status) {
        return VERIFIABLE.contains(status);
    }
}
