package com.huaweicloud.hdgatewayservice.controller;

import com.huaweicloud.hdgatewayservice.model.ErrorResponse;
import com.huaweicloud.hdgatewayservice.service.InvokeService;
import com.huaweicloud.hdgatewayservice.transport.Transport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private static final String INVOKE_PATH = "/rest/developer/server/hdgatewayservice/invoke";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void routeNotFoundMapsTo404() {
        ResponseEntity<ErrorResponse> resp = handler.handleInvoke(
                new InvokeService.InvokeException("HDGW_ROUTE_NOT_FOUND", "route nope", null));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals("HDGW_ROUTE_NOT_FOUND", resp.getBody().code());
        assertNotNull(resp.getBody().traceId());
    }

    @Test
    void methodNotAllowedMapsTo405() {
        ResponseEntity<ErrorResponse> resp = handler.handleInvoke(
                new InvokeService.InvokeException("HDGW_METHOD_NOT_ALLOWED", "GET", null));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
    }

    @Test
    void rateLimitedMapsTo429() {
        ResponseEntity<ErrorResponse> resp = handler.handleInvoke(
                new InvokeService.InvokeException("HDGW_RATE_LIMITED", "Too many requests", null));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.getStatusCode());
    }

    @Test
    void upstreamErrorMapsTo502() {
        ResponseEntity<ErrorResponse> resp = handler.handleUpstream(
                new Transport.UpstreamException("boom", null, false));
        assertEquals(HttpStatus.BAD_GATEWAY, resp.getStatusCode());
        assertEquals("HDGW_UPSTREAM_ERROR", resp.getBody().code());
    }

    @Test
    void upstreamTimeoutMapsTo504() {
        ResponseEntity<ErrorResponse> resp = handler.handleUpstream(
                new Transport.UpstreamException("timeout", null, true));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, resp.getStatusCode());
        assertEquals("HDGW_TIMEOUT", resp.getBody().code());
    }

    @Test
    void routeForbiddenMapsTo403() {
        ResponseEntity<ErrorResponse> resp = handler.handleInvoke(
                new InvokeService.InvokeException("HDGW_ROUTE_FORBIDDEN", "not allowed", null));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void unknownCodeMapsTo500() {
        ResponseEntity<ErrorResponse> resp = handler.handleInvoke(
                new InvokeService.InvokeException("HDGW_WEIRD", "?", null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }

    @Test
    void unquotedKeyBodyMapsTo400WithJacksonReason() throws Exception {
        mockMvc().perform(post(INVOKE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{route:hdgrowservice-get-order-list,body:{page_no:1,page_size:2}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HDGW_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("double-quote")))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void emptyBodyMapsTo400() throws Exception {
        mockMvc().perform(post(INVOKE_PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HDGW_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void nonJsonTextMapsTo400() throws Exception {
        mockMvc().perform(post(INVOKE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HDGW_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Unrecognized token")))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void validJsonBodyStillMapsTo200() throws Exception {
        InvokeService service = mock(InvokeService.class);
        when(service.invoke(any(), any())).thenReturn(ResponseEntity.ok("{}"));
        mockMvc(service).perform(post(INVOKE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"route\":\"hdgrowservice-get-order-list\","
                                + "\"body\":{\"page_no\":1,\"page_size\":2}}"))
                .andExpect(status().isOk());
    }

    private MockMvc mockMvc() {
        return mockMvc(mock(InvokeService.class));
    }

    private MockMvc mockMvc(InvokeService service) {
        return MockMvcBuilders.standaloneSetup(new InvokeController(service))
                .setControllerAdvice(handler)
                .build();
    }
}
