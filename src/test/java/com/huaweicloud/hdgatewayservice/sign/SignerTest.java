package com.huaweicloud.hdgatewayservice.sign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignerTest {

    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void sha256HexKnownValue() {
        assertEquals(EMPTY_SHA256, Signer.sha256Hex(""));
        assertEquals(64, Signer.sha256Hex("abc").length());
    }

    @Test
    void signatureFormatWithoutToken() {
        Signer.SignResult r = Signer.sign("POST", "/open-api-public/v1/check", "",
                "{\"a\":1}", "AK123", "SK456", "his.example.com", "20260101T000000Z");
        assertTrue(r.authorization().startsWith("SDK-HMAC-SHA256 Access=AK123, "));
        assertTrue(r.authorization().contains("SignedHeaders=host;x-sdk-date, "));
        assertTrue(r.authorization().matches(".*Signature=[0-9a-f]{64}$"));
        assertEquals("20260101T000000Z", r.timestamp());
    }

    @Test
    void signatureFormatWithSecurityToken() {
        Signer.SignResult r = Signer.signWithToken("GET", "/v3/auth/domains", "", "",
                "AK123", "SK456", "STS-TOKEN", "iam.myhuaweicloud.com");
        assertTrue(r.authorization().contains("SignedHeaders=host;x-sdk-date;x-security-token, "));
        assertTrue(r.authorization().matches(".*Signature=[0-9a-f]{64}$"));
    }

    @Test
    void signatureIsDeterministicForSameInputs() {
        Signer.SignResult a = Signer.sign("POST", "/x", "", "body", "AK", "SK", "h", "T");
        Signer.SignResult b = Signer.sign("POST", "/x", "", "body", "AK", "SK", "h", "T");
        assertEquals(a.authorization(), b.authorization());
    }

    @Test
    void signatureDiffersForDifferentBody() {
        Signer.SignResult a = Signer.sign("POST", "/x", "", "body1", "AK", "SK", "h", "T");
        Signer.SignResult b = Signer.sign("POST", "/x", "", "body2", "AK", "SK", "h", "T");
        assertTrue(!a.authorization().equals(b.authorization()));
    }
}
