package com.huaweicloud.hdgatewayservice.util;

import com.huaweicloud.hdgatewayservice.config.GatewayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaskerTest {

    private Masker masker;

    @BeforeEach
    void setUp() {
        GatewayConfig config = new GatewayConfig();
        ReflectionTestUtils.setField(config, "maskKeys",
                "password,passwd,pwd,secret,token,sk,access_key,private_key,authorization,cookie,credential,appcode,app_code,api_key,apikey");
        masker = new Masker(config);
    }

    @Test
    void masksSensitiveJsonFields() {
        String out = masker.mask("{\"password\":\"super-secret\",\"name\":\"app\"}");
        assertFalse(out.contains("super-secret"));
        assertTrue(out.contains("app"));
    }

    @Test
    void keepsNonSensitiveFields() {
        String out = masker.mask("{\"route\":\"his-demo\",\"path\":\"/v1/check\"}");
        assertEquals("{\"route\":\"his-demo\",\"path\":\"/v1/check\"}", out);
    }

    @Test
    void nonJsonTextPassthrough() {
        assertEquals("plain text", masker.mask("plain text"));
    }

    @Test
    void emptyAndNullReturnEmpty() {
        assertEquals("", masker.mask(null));
        assertEquals("", masker.mask(""));
    }

    @Test
    void longTextTruncated() {
        String longText = "x".repeat(3000);
        String out = masker.mask(longText);
        assertTrue(out.length() < 3000);
        assertTrue(out.endsWith("...(truncated)"));
    }

    @Test
    void maskQueryMasksSensitiveParams() {
        String out = masker.maskQuery("a=1&token=abc123&b=2");
        assertFalse(out.contains("abc123"));
        assertTrue(out.contains("a=1"));
        assertTrue(out.contains("b=2"));
    }

    @Test
    void controlCharsStripped() {
        String out = masker.mask("{\"a\":1}\u0000\u0007");
        assertFalse(out.contains("\u0000"));
        assertFalse(out.contains("\u0007"));
    }
}
