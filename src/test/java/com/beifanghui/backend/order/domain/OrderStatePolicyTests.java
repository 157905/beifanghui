package com.beifanghui.backend.order.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatePolicyTests {
    @Test
    void 只有待支付订单可以支付() {
        assertTrue(OrderStatePolicy.canPay("PENDING_PAYMENT"));
        assertFalse(OrderStatePolicy.canPay("PAID"));
        assertFalse(OrderStatePolicy.canPay("CANCELLED"));
    }

    @Test
    void 只有已支付和待使用订单可以退款() {
        assertTrue(OrderStatePolicy.canRefund("PAID"));
        assertTrue(OrderStatePolicy.canRefund("READY"));
        assertFalse(OrderStatePolicy.canRefund("PENDING_PAYMENT"));
        assertFalse(OrderStatePolicy.canRefund("COMPLETED"));
        assertFalse(OrderStatePolicy.canRefund("REFUNDED"));
    }

    @Test
    void 只有已支付和待使用订单可以核销() {
        assertTrue(OrderStatePolicy.canVerify("PAID"));
        assertTrue(OrderStatePolicy.canVerify("READY"));
        assertFalse(OrderStatePolicy.canVerify("CANCELLED"));
        assertFalse(OrderStatePolicy.canVerify("REFUNDED"));
    }
}
