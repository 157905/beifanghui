package com.beifanghui.backend.setting.api;

import java.time.LocalDateTime;

public record PlatformSettingResponse(
        String key, String value, String valueType, boolean publicSetting,
        String description, Long updatedBy, LocalDateTime updatedAt) {
}
