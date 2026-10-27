package com.huaweicloud.hdgatewayservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GatewayConfig {

    @Value("${hdgateway.his.app-code:}")
    private String hisAppCode;

    @Value("${hdgateway.his.hw-id:}")
    private String hisHwId;

    @Value("${hdgateway.his.app-key:}")
    private String hisAppKey;

    @Value("${hdgateway.his.auth-token:}")
    private String hisAuthToken;

    @Value("${hdgateway.his.iam-endpoint:https://iam.cn-north-4.myhuaweicloud.com/v3/auth/tokens}")
    private String hisIamEndpoint;

    @Value("${hdgateway.his.iam-username:}")
    private String hisIamUsername;

    @Value("${hdgateway.his.iam-password:}")
    private String hisIamPassword;

    @Value("${hdgateway.his.iam-domain-name:}")
    private String hisIamDomainName;

    @Value("${hdgateway.app-code:}")
    private String globalAppCode;

    @Value("${hdgateway.deploy-env:production}")
    private String deployEnv;

    @Value("${hdgateway.log-mask-keys:password,passwd,pwd,secret,token,sk,access_key,private_key,authorization,cookie,credential,appcode,app_code,api_key,apikey}")
    private String maskKeys;

    @Value("${hdgateway.connect-timeout-ms:10000}")
    private long connectTimeoutMs;

    @Value("${hdgateway.read-timeout-ms:30000}")
    private long readTimeoutMs;

    @Value("${hdgateway.caller-header-deny:host,content-length,authorization,cookie,connection,transfer-encoding,expect,x-apig,x-hw,x-auth,x-api-key,x-traceid}")
    private String callerHeaderDeny;

    public String hisAppCode() { return hisAppCode; }
    public String hisHwId() { return hisHwId; }
    public String hisAppKey() { return hisAppKey; }
    public String hisAuthToken() { return hisAuthToken; }
    public String hisIamEndpoint() { return hisIamEndpoint; }
    public String hisIamUsername() { return hisIamUsername; }
    public String hisIamPassword() { return hisIamPassword; }
    public String hisIamDomainName() { return hisIamDomainName; }
    public String globalAppCode() { return globalAppCode; }
    public String deployEnv() { return deployEnv; }
    public long connectTimeoutMs() { return connectTimeoutMs; }
    public long readTimeoutMs() { return readTimeoutMs; }

    public List<String> maskKeys() {
        List<String> out = new ArrayList<>();
        if (maskKeys == null || maskKeys.isBlank()) return out;
        for (String part : maskKeys.split(",")) {
            String key = part.trim().toLowerCase();
            if (!key.isEmpty()) out.add(key);
        }
        return out;
    }

    /** 调用方透传头黑名单：精确项 + 前缀项（x-apig/x-hw/x-auth/x-api-key/x-traceid） */
    public List<String> callerHeaderDenyList() {
        List<String> out = new ArrayList<>();
        if (callerHeaderDeny == null || callerHeaderDeny.isBlank()) return out;
        for (String part : callerHeaderDeny.split(",")) {
            String key = part.trim().toLowerCase();
            if (!key.isEmpty()) out.add(key);
        }
        return out;
    }
}
