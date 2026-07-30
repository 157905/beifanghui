package com.beifanghui.backend.identity.domain;

import java.util.List;

public record IdentityPrincipal(
        String userId,
        String displayName,
        String accountType,
        List<String> roles) {
}
