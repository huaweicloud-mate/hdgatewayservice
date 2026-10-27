package com.huaweicloud.hdgatewayservice.model;

import java.util.List;

/** 协议签署结果：signed=true 全流程成功；signedCount=本次实际签署数（0=已全部签署，无需签署） */
public record AgreementSignResponse(boolean signed, int signedCount, List<Item> results) {

    public record Item(int agrType, int signStatus, long latestVersion, boolean signed) {
    }
}
