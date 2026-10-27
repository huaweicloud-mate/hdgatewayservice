package com.huaweicloud.hdgatewayservice.controller;

import com.huaweicloud.hdgatewayservice.model.ApiKey;
import com.huaweicloud.hdgatewayservice.repository.ApiKeyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API Key 管理（一个调用方一个 key，admin JWT 保护）：重复创建同 source 即轮换；
 * routes 支持指定 key 级路由授权（逗号分隔，空 = 不限） */
@RestController
@RequestMapping("/rest/developer/server/hdgatewayservice/api-keys")
public class ApiKeyController {

    private final ApiKeyRepository repository;

    public ApiKeyController(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createKey(@RequestParam String source,
                                                         @RequestParam(required = false) String routes) {
        String caller = source == null ? "" : source.trim();
        if (caller.isEmpty() || caller.length() > 64) {
            return ResponseEntity.badRequest().body(Map.of("error", "source 必填且不超过 64 字符"));
        }
        String allowedRoutes = routes == null ? "" : routes.trim();
        if (allowedRoutes.length() > 512) {
            return ResponseEntity.badRequest().body(Map.of("error", "routes 不超过 512 字符"));
        }
        boolean rotated = false;
        ApiKey apiKey = repository.findBySource(caller).orElse(null);
        if (apiKey != null) {
            apiKey.setApiKey(UUID.randomUUID().toString().replace("-", ""));
            apiKey.setAllowedRoutes(allowedRoutes);
            apiKey.setEnabled(true);
            rotated = true;
        } else {
            apiKey = new ApiKey(UUID.randomUUID().toString().replace("-", ""), caller, allowedRoutes);
        }
        repository.save(apiKey);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", apiKey.getId());
        out.put("apiKey", apiKey.getApiKey());
        out.put("source", apiKey.getSource());
        out.put("routes", apiKey.allowedRouteSet());
        out.put("rotated", rotated);
        return ResponseEntity.ok(out);
    }

    @GetMapping
    public List<ApiKey> listKeys() {
        return repository.findAll();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> disableKey(@PathVariable Long id) {
        ApiKey apiKey = repository.findById(id).orElse(null);
        if (apiKey == null) {
            return ResponseEntity.status(404).body(Map.of("error", "API key not found"));
        }
        apiKey.setEnabled(false);
        repository.save(apiKey);
        return ResponseEntity.ok(Map.of("status", "disabled"));
    }
}
