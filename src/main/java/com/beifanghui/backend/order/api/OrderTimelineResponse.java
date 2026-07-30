package com.beifanghui.backend.order.api;

import java.time.LocalDateTime;
import java.util.List;

public record OrderTimelineResponse(long orderId, String orderNo, String currentStatus,
                                    List<Event> events) {
    public record Event(String eventType, String status, Long amountCent,
                        String referenceNo, String note, LocalDateTime eventTime) {}
}
