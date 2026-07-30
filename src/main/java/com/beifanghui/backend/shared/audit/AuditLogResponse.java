package com.beifanghui.backend.shared.audit;

import java.time.LocalDateTime;

public record AuditLogResponse(long id, Long operatorId, String operatorName, String action,
                               String targetType, String targetId, String detail,
                               LocalDateTime createdAt) {}
