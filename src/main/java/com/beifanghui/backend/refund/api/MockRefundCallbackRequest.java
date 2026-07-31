package com.beifanghui.backend.refund.api;

public record MockRefundCallbackRequest(String channelRefundId, String status, String message) {
}
