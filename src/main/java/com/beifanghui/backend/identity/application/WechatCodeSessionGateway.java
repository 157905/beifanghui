package com.beifanghui.backend.identity.application;

import com.beifanghui.backend.identity.domain.WechatCodeSession;

public interface WechatCodeSessionGateway {

    WechatCodeSession exchange(String loginCode);
}
