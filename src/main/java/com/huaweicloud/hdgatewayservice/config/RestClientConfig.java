package com.huaweicloud.hdgatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient gatewayRestClient(CallLogInterceptor callLogInterceptor, GatewayConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(config.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(config.readTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(callLogInterceptor)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
