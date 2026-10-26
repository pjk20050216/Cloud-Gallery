package com.yupi.model.enums;

import lombok.Getter;

/**
 * 封禁记录状态
 */
@Getter
public enum BanStatusEnum {

    ACTIVE(0, "生效中"),
    RELEASED(1, "已解封"),
    EXPIRED(2, "已过期");

    private final int value;

    private final String text;

    BanStatusEnum(int value, String text) {
        this.value = value;
        this.text = text;
    }
}
