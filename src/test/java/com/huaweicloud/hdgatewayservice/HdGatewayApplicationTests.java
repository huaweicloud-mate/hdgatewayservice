package com.huaweicloud.hdgatewayservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HdGatewayApplicationTests {

    @MockBean
    private com.huaweicloud.hdgatewayservice.service.InvokeService invokeService;

    @MockBean
    private com.huaweicloud.hdgatewayservice.service.LoginAttemptService loginAttemptService;

    @Test
    void contextLoads() {
    }
}
