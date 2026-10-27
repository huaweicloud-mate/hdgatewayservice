package com.huaweicloud.hdgatewayservice.config;

import com.huaweicloud.hdgatewayservice.util.Masker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class CallLogInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger callLog = LoggerFactory.getLogger("com.huaweicloud.hdgatewayservice.call");

    private final Masker masker;

    public CallLogInterceptor(Masker masker) {
        this.masker = masker;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String method = request.getMethod().name();
        String uri = request.getURI().toString();
        String traceId = MDC.get("traceID");
        if (traceId != null && !traceId.isEmpty()) {
            request.getHeaders().set("x-traceID", traceId);
        }
        long start = System.currentTimeMillis();
        callLog.info("[call] {} {} req={}", method, uri, maskBody(body));
        try {
            ClientHttpResponse response = execution.execute(request, body);
            BufferingClientHttpResponseWrapper wrapper = new BufferingClientHttpResponseWrapper(response);
            long duration = System.currentTimeMillis() - start;
            callLog.info("[call] {} {} <- status={} dur={}ms resp={}",
                    method, uri, wrapper.getStatusCode().value(), duration, maskBody(wrapper.body()));
            return wrapper;
        } catch (IOException ex) {
            logFailure(method, uri, start, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            logFailure(method, uri, start, ex.getMessage());
            throw ex;
        }
    }

    private String maskBody(byte[] body) {
        if (body == null || body.length == 0) return "";
        return masker.mask(new String(body, StandardCharsets.UTF_8));
    }

    private void logFailure(String method, String uri, long start, String err) {
        long duration = System.currentTimeMillis() - start;
        callLog.error("[call] {} {} <- failed err={} dur={}ms", method, uri, masker.mask(err), duration);
    }

    private static final class BufferingClientHttpResponseWrapper implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        BufferingClientHttpResponseWrapper(ClientHttpResponse response) throws IOException {
            this.delegate = response;
            try (InputStream in = response.getBody()) {
                this.body = in.readAllBytes();
            }
        }

        byte[] body() {
            return body;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
