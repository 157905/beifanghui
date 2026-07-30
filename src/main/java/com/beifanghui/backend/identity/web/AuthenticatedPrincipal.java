package com.beifanghui.backend.identity.web;

import java.util.List;

public record AuthenticatedPrincipal(
        String userId,
        String displayName,
        String accountType,
        List<String> roles) {
}
