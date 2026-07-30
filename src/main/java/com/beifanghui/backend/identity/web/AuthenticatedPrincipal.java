package com.beifanghui.backend.identity.web;

import java.util.List;

public record AuthenticatedPrincipal(
        String userId,
        String displayName,
        String accountType,
        List<String> roles,
        String identityProvider,
        String externalIdentity) {

    public AuthenticatedPrincipal(
            String userId,
            String displayName,
            String accountType,
            List<String> roles) {
        this(userId, displayName, accountType, roles, "MOCK", null);
    }

    public String databaseOpenId() {
        if ("WECHAT".equals(identityProvider) && externalIdentity != null && !externalIdentity.isBlank()) {
            return externalIdentity;
        }
        return "mock:" + userId;
    }
}
