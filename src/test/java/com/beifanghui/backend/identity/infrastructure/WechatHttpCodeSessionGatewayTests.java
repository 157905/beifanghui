package com.beifanghui.backend.identity.infrastructure;

import com.beifanghui.backend.identity.domain.WechatCodeSession;
import com.beifanghui.backend.shared.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatHttpCodeSessionGatewayTests {

    private WechatHttpCodeSessionGateway gateway;
    private MockRestServiceServer server;

    @BeforeEach
    void 创建微信接口模拟服务() {
        WechatLoginProperties properties = new WechatLoginProperties();
        properties.setAppId("test-app");
        properties.setAppSecret("test-secret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new WechatHttpCodeSessionGateway(properties, builder.build());
    }

    @Test
    void 正确解析微信Code2Session结果() {
        server.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=test-app&secret=test-secret&js_code=test-code&grant_type=authorization_code"))
                .andRespond(withSuccess("""
                        {"openid":"openid-001","session_key":"session-key-001","unionid":"unionid-001"}
                        """, MediaType.APPLICATION_JSON));

        WechatCodeSession result = gateway.exchange("test-code");

        assertEquals("openid-001", result.openId());
        assertEquals("session-key-001", result.sessionKey());
        assertEquals("unionid-001", result.unionId());
        server.verify();
    }

    @Test
    void 微信Code无效时返回参数错误() {
        server.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=test-app&secret=test-secret&js_code=invalid-code&grant_type=authorization_code"))
                .andRespond(withSuccess("""
                        {"errcode":40029,"errmsg":"invalid code"}
                        """, MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> gateway.exchange("invalid-code"));

        assertEquals("SYSTEM_400_001", exception.errorCode().code());
        server.verify();
    }
}
