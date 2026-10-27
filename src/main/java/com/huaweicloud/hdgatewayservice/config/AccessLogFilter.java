package com.huaweicloud.hdgatewayservice.config;

import com.huaweicloud.hdgatewayservice.util.Masker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.huaweicloud.hdgatewayservice.interface");

    private final Masker masker;

    public AccessLogFilter(Masker masker) {
        this.masker = masker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader("x-traceID");
        MDC.put("traceID", (traceId != null && !traceId.isEmpty()) ? traceId : UUID.randomUUID().toString().replace("-", ""));
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(wrapped, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - start;
            String reqBody = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
            String respBody = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            if (query != null && !query.isEmpty()) {
                uri = uri + "?" + masker.maskQuery(query);
            }
            log.info("[interface] {} {} status={} dur={}ms key={} req={} resp={}",
                    request.getMethod(),
                    uri,
                    responseWrapper.getStatus(),
                    duration,
                    mask(request.getHeader("X-Api-Key")),
                    masker.mask(reqBody),
                    masker.mask(respBody));
            try {
                responseWrapper.copyBodyToResponse();
            } catch (IOException ignored) {
            }
            MDC.remove("traceID");
        }
    }

    private String mask(String key) {
        if (key == null || key.isEmpty()) return "-";
        if (key.length() <= 8) return "***";
        return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
    }
}
