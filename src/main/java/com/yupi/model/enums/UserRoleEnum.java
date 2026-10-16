package com.yupi.model.enums;


// 因为用户的角色就俩种：一个是普通用户，一个是管理员，所以可以进行枚举

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;


@Getter
public enum UserRoleEnum {

    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }


    // 根据 value 获取枚举
    public static UserRoleEnum getTextByValue(String value) {
        if (ObjectUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            if (userRoleEnum.value.equals(value)) {
                return userRoleEnum;
            }
        }
        return null;
    }

}
