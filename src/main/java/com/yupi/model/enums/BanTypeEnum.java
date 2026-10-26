package com.yupi.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 处罚类型
 */
@Getter
public enum BanTypeEnum {

    BAN(1, "封禁", "账号被限制登录后的全部写操作"),
    MUTE(2, "禁言", "不能发表评论等内容"),
    UPLOAD_LIMIT(3, "限制上传", "不能上传图片");

    private final int value;

    private final String text;

    private final String description;

    BanTypeEnum(int value, String text, String description) {
        this.value = value;
        this.text = text;
        this.description = description;
    }

    public static BanTypeEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (BanTypeEnum anEnum : values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }

    public static String getTextByValue(Integer value) {
        BanTypeEnum anEnum = getEnumByValue(value);
        return anEnum == null ? "处罚" : anEnum.getText();
    }
}
