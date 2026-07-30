package com.beifanghui.backend.shared.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Duration window = Duration.ofMinutes(1);
    private int loginLimit = 10;
    private int createOrderLimit = 20;
    private int paymentLimit = 10;
    private int refundLimit = 10;
    private int verificationLimit = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public int getLoginLimit() {
        return loginLimit;
    }

    public void setLoginLimit(int loginLimit) {
        this.loginLimit = loginLimit;
    }

    public int getCreateOrderLimit() {
        return createOrderLimit;
    }

    public void setCreateOrderLimit(int createOrderLimit) {
        this.createOrderLimit = createOrderLimit;
    }

    public int getPaymentLimit() {
        return paymentLimit;
    }

    public void setPaymentLimit(int paymentLimit) {
        this.paymentLimit = paymentLimit;
    }

    public int getRefundLimit() {
        return refundLimit;
    }

    public void setRefundLimit(int refundLimit) {
        this.refundLimit = refundLimit;
    }

    public int getVerificationLimit() {
        return verificationLimit;
    }

    public void setVerificationLimit(int verificationLimit) {
        this.verificationLimit = verificationLimit;
    }
}
