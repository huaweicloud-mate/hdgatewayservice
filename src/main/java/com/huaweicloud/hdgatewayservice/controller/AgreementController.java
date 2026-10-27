package com.huaweicloud.hdgatewayservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.huaweicloud.hdgatewayservice.model.AgreementSignRequest;
import com.huaweicloud.hdgatewayservice.model.AgreementSignResponse;
import com.huaweicloud.hdgatewayservice.ratelimit.RateLimit;
import com.huaweicloud.hdgatewayservice.service.AgreementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/developer/server/hdgatewayservice")
@RateLimit(name = "agreement", key = RateLimit.KeyType.CREDENTIAL, capacity = 40, refillPerSecond = 20)
public class AgreementController {

    private final AgreementService service;

    public AgreementController(AgreementService service) {
        this.service = service;
    }

    /** 查询协议签署状态（封装 query_sign_info_list 组装），透传 sign_info_list */
    @PostMapping("/agreement/query")
    public ResponseEntity<JsonNode> query(@Valid @RequestBody AgreementSignRequest req) {
        return ResponseEntity.ok(service.query(req));
    }

    /** 签署协议（蓝本式编排）：查询 → 过滤未签/旧版 → 签署最新版 → 返回结果 */
    @PostMapping("/agreement/sign")
    public AgreementSignResponse sign(@Valid @RequestBody AgreementSignRequest req) {
        return service.sign(req);
    }
}
