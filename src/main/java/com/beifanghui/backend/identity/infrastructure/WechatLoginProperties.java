package com.beifanghui.backend.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.wechat")
public class WechatLoginProperties {

    private String appId;
    private String appSecret;
    private String code2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getCode2SessionUrl() {
        return code2SessionUrl;
    }

    public void setCode2SessionUrl(String code2SessionUrl) {
        this.code2SessionUrl = code2SessionUrl;
    }
}
