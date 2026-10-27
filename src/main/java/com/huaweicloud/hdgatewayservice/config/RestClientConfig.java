package com.huaweicloud.hdgatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient gatewayRestClient(CallLogInterceptor callLogInterceptor, GatewayConfig config) {
        // JDK HttpClient：4xx/5xx 也正常返回响应体（SimpleClientHttpRequestFactory 会抛 IOException 吞错误体）
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(config.readTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(callLogInterceptor)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
