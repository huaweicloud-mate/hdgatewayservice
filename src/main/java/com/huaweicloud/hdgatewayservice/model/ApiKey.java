package com.huaweicloud.hdgatewayservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 一个调用方一个 API Key：source 唯一，重复创建即轮换（旧 key 被替换）；
 * allowed_routes 为逗号分隔的路由白名单（空 = 不限，可调所有已注册路由） */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key", length = 128, nullable = false, unique = true)
    private String apiKey;

    @Column(name = "source", length = 64, nullable = false, unique = true)
    private String source;

    @Column(name = "allowed_routes", length = 512, nullable = false)
    private String allowedRoutes = "";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ApiKey() {
    }

    public ApiKey(String apiKey, String source) {
        this(apiKey, source, "");
    }

    public ApiKey(String apiKey, String source, String allowedRoutes) {
        this.apiKey = apiKey;
        this.source = source;
        this.allowedRoutes = allowedRoutes == null ? "" : allowedRoutes.trim();
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getAllowedRoutes() { return allowedRoutes; }
    public void setAllowedRoutes(String allowedRoutes) {
        this.allowedRoutes = allowedRoutes == null ? "" : allowedRoutes.trim();
    }

    /** 允许的路由名（小写）；空集合 = 不限 */
    public Set<String> allowedRouteSet() {
        if (allowedRoutes == null || allowedRoutes.isBlank()) return Set.of();
        return Arrays.stream(allowedRoutes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
