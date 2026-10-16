package com.yupi.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

@Getter
public enum ReportStatusEnum {

    PENDING("待处理", 0),
    APPROVED("已下架", 1),
    REJECTED("已驳回", 2);

    private final String text;
    private final int value;

    ReportStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    public static ReportStatusEnum getByValue(Integer value) {
        if (ObjectUtil.isEmpty(value)) {
            return null;
        }
        for (ReportStatusEnum e : ReportStatusEnum.values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
