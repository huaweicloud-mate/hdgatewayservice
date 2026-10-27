package com.huaweicloud.hdgatewayservice.config;

import com.huaweicloud.hdgatewayservice.model.ApiKey;
import com.huaweicloud.hdgatewayservice.repository.ApiKeyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyInterceptorTest {

    @Mock
    private ApiKeyRepository repository;

    private ApiKeyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ApiKeyInterceptor(repository);
    }

    @Test
    void missingHeaderReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invoke");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
    }

    @Test
    void invalidOrDisabledKeyReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invoke");
        request.addHeader("X-Api-Key", "bad-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(repository.findByApiKeyAndEnabledTrue("bad-key")).thenReturn(Optional.empty());

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
    }

    @Test
    void blankKeyReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invoke");
        request.addHeader("X-Api-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
    }

    @Test
    void validKeyPassesAndSetsSource() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invoke");
        request.addHeader("X-Api-Key", "good-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(repository.findByApiKeyAndEnabledTrue("good-key"))
                .thenReturn(Optional.of(new ApiKey("good-key", "caller-a")));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals("caller-a", request.getAttribute("source"));
        assertEquals(java.util.Set.of(), request.getAttribute("allowedRoutes"));
    }

    @Test
    void validKeySetsAllowedRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/invoke");
        request.addHeader("X-Api-Key", "scoped-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(repository.findByApiKeyAndEnabledTrue("scoped-key"))
                .thenReturn(Optional.of(new ApiKey("scoped-key", "caller-b", "HIS-Demo, direct")));

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals(java.util.Set.of("his-demo", "direct"), request.getAttribute("allowedRoutes"));
    }

    @Test
    void optionsBypassesAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/invoke");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertNull(request.getAttribute("source"));
        assertNull(request.getAttribute("allowedRoutes"));
    }
}
