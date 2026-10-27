package com.huaweicloud.hdgatewayservice.controller;

import com.huaweicloud.hdgatewayservice.model.InvokeRequest;
import com.huaweicloud.hdgatewayservice.ratelimit.RateLimit;
import com.huaweicloud.hdgatewayservice.service.InvokeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/rest/developer/server/hdgatewayservice")
@RateLimit(name = "invoke", key = RateLimit.KeyType.CREDENTIAL, capacity = 200, refillPerSecond = 100)
public class InvokeController {

    private final InvokeService service;

    public InvokeController(InvokeService service) {
        this.service = service;
    }

    /** 核心 invoke 接口：蓝区应用经网关调用绿区 HIS/APIG 接口，上游响应透传；
     * allowedRoutes 由 ApiKeyInterceptor 从 X-Api-Key 注入（key 级路由授权） */
    @PostMapping("/invoke")
    public ResponseEntity<String> invoke(@Valid @RequestBody InvokeRequest req,
                                         HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Set<String> allowedRoutes = (Set<String>) request.getAttribute("allowedRoutes");
        return service.invoke(req, allowedRoutes);
    }

    /** 路由白名单列表（不暴露上游 URL 与凭证） */
    @GetMapping("/routes")
    public List<com.huaweicloud.hdgatewayservice.model.RouteInfo> routes() {
        return service.routes();
    }
}
