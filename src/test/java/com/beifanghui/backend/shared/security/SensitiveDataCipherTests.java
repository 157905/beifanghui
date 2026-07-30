package com.beifanghui.backend.shared.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataCipherTests {
    private final SensitiveDataCipher cipher = new SensitiveDataCipher("测试环境至少三十二位长度的独立随机密钥-123456");

    @Test
    void 加密后可以正确解密且密文不包含明文() {
        String source="110101199001011234";
        byte[] encrypted=cipher.encrypt(source);

        assertEquals(source,cipher.decrypt(encrypted));
        assertNotEquals(source,new String(encrypted, StandardCharsets.UTF_8));
    }

    @Test
    void 相同内容每次生成不同密文防止数据特征泄露() {
        byte[] first=cipher.encrypt("张三");
        byte[] second=cipher.encrypt("张三");

        assertFalse(Arrays.equals(first,second));
        assertEquals("张三",cipher.decrypt(first));
        assertEquals("张三",cipher.decrypt(second));
    }

    @Test
    void 检索摘要稳定且不等于原始证件号() {
        String source="110101199001011234";
        String first=cipher.searchHash(source);
        String second=cipher.searchHash(source);

        assertEquals(first,second);
        assertEquals(64,first.length());
        assertNotEquals(source,first);
    }
}
