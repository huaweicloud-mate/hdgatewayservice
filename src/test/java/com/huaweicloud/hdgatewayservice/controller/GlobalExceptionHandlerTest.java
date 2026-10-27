package com.huaweicloud.hdgatewayservice.controller;

import com.huaweicloud.hdgatewayservice.model.ErrorResponse;
import com.huaweicloud.hdgatewayservice.service.InvokeService;
import com.huaweicloud.hdgatewayservice.transport.Transport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

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
}
