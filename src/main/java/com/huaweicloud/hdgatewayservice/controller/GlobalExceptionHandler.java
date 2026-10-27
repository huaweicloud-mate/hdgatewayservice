package com.huaweicloud.hdgatewayservice.controller;

import com.huaweicloud.hdgatewayservice.model.ErrorResponse;
import com.huaweicloud.hdgatewayservice.service.InvokeService;
import com.huaweicloud.hdgatewayservice.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, HttpStatus> STATUS_MAP = Map.ofEntries(
            Map.entry("HDGW_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS),
            Map.entry("HDGW_INVALID_REQUEST", HttpStatus.BAD_REQUEST),
            Map.entry("HDGW_UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
            Map.entry("HDGW_ROUTE_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("HDGW_ROUTE_FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("HDGW_METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED),
            Map.entry("HDGW_ROUTE_NOT_CONFIGURED", HttpStatus.BAD_GATEWAY),
            Map.entry("HDGW_UPSTREAM_ERROR", HttpStatus.BAD_GATEWAY),
            Map.entry("HDGW_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT));

    @ExceptionHandler(InvokeService.InvokeException.class)
    public ResponseEntity<ErrorResponse> handleInvoke(InvokeService.InvokeException e) {
        log.error("[error] {}: {}", e.code(), e.getMessage());
        HttpStatus status = STATUS_MAP.getOrDefault(e.code(), HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(status)
                .body(new ErrorResponse(e.code(), e.getMessage(), traceId()));
    }

    @ExceptionHandler(Transport.UpstreamException.class)
    public ResponseEntity<ErrorResponse> handleUpstream(Transport.UpstreamException e) {
        String code = e.isTimeout() ? "HDGW_TIMEOUT" : "HDGW_UPSTREAM_ERROR";
        log.error("[error] {}: {}", code, e.getMessage());
        HttpStatus status = STATUS_MAP.getOrDefault(code, HttpStatus.BAD_GATEWAY);
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, e.getMessage(), traceId()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("HDGW_INVALID_REQUEST", "缺少请求头 " + e.getHeaderName(),
                        traceId()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("HDGW_INVALID_REQUEST", "缺少请求参数 " + e.getParameterName(),
                        traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("请求参数不合法");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("HDGW_INVALID_REQUEST", msg, traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e) {
        log.error("[error] unexpected: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("HDGW_INTERNAL", "服务内部错误", traceId()));
    }

    private String traceId() {
        String id = MDC.get("traceID");
        return id != null ? id : UUID.randomUUID().toString().replace("-", "");
    }
}
