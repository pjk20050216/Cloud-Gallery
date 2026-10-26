package com.yupi.model.enums;

import lombok.Getter;

/**
 * 申诉状态
 */
@Getter
public enum AppealStatusEnum {

    NONE(0, "未申诉"),
    PENDING(1, "申诉中"),
    APPROVED(2, "申诉通过"),
    REJECTED(3, "申诉驳回");

    private final int value;

    private final String text;

    AppealStatusEnum(int value, String text) {
        this.value = value;
        this.text = text;
    }

    public static String getTextByValue(Integer value) {
        if (value == null) {
            return NONE.getText();
        }
        for (AppealStatusEnum anEnum : values()) {
            if (anEnum.value == value) {
                return anEnum.getText();
            }
        }
        return NONE.getText();
    }
}
