package com.beifanghui.backend.setting.api;

import java.time.LocalDateTime;

public record AuditLogResponse(
        long id, Long operatorId, String operatorName, String action,
        String targetType, String targetId, String detail,
        String ipAddress, String traceId, LocalDateTime createdAt) {
}
