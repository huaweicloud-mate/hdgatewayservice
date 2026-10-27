package com.huaweicloud.hdgatewayservice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 协议查询/签署请求：developer_id 必填，agrTypes 协议类型列表，country 默认 cn，language 默认 zh_cn */
public record AgreementSignRequest(
        @NotBlank(message = "developerId 必填") String developerId,
        @NotEmpty(message = "agrTypes 必填") List<Integer> agrTypes,
        String country,
        String language) {
}
