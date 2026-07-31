package com.beifanghui.backend.setting.api;

import java.util.List;

public record RolePermissionsUpdateRequest(List<Long> permissionIds) {
}
