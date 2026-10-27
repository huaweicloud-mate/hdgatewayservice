package com.huaweicloud.hdgatewayservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** API Key 由运维直接在数据库 api_key 表配置，本服务只读校验 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyInterceptor apiKeyInterceptor;

    public WebMvcConfig(ApiKeyInterceptor apiKeyInterceptor) {
        this.apiKeyInterceptor = apiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns(
                        "/rest/developer/server/hdgatewayservice/invoke",
                        "/rest/developer/server/hdgatewayservice/routes",
                        "/rest/developer/server/hdgatewayservice/agreement/**");
    }
}
