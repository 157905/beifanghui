package com.beifanghui.backend.identity.api;

public record WechatLoginRequest(String code, String displayName) {
}
