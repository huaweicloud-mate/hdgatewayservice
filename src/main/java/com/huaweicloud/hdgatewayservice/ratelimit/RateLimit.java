package com.huaweicloud.hdgatewayservice.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：方法级优先，类级兜底（类级对整个 Controller 生效）。
 * 底层为 Redis Token Bucket（Lua 原子扣减，多 pod 共享计数）。
 *
 * name    规则名（桶 key 前缀，同名的多个接口共享同一个桶）
 * key     按谁限流（IP / CREDENTIAL，缺失时自动回退 IP）
 * capacity        桶容量（允许的最大突发）
 * refillPerSecond 每秒补充速率（稳态阈值）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String name();

    KeyType key() default KeyType.IP;

    int capacity();

    int refillPerSecond();

    enum KeyType {
        /** 客户端 IP（XFF 最右侧，回退 remoteAddr） */
        IP,
        /** 业务凭证：X-Api-Key，缺失回退 IP */
        CREDENTIAL
    }
}
