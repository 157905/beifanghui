package com.beifanghui.backend.setting.api;

import java.util.List;

public record RoleResponse(
        long id, String code, String name, long userCount,
        List<Long> permissionIds, List<String> permissionCodes) {
}
