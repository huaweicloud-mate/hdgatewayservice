package com.huaweicloud.hdgatewayservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ApiKeyInterceptor apiKeyInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, ApiKeyInterceptor apiKeyInterceptor) {
        this.authInterceptor = authInterceptor;
        this.apiKeyInterceptor = apiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/rest/developer/server/hdgatewayservice/api-keys/**");

        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns(
                        "/rest/developer/server/hdgatewayservice/invoke",
                        "/rest/developer/server/hdgatewayservice/routes");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/rest/developer/server/hdgatewayservice/api-keys/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        registry.addMapping("/rest/developer/server/hdgatewayservice/auth/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
