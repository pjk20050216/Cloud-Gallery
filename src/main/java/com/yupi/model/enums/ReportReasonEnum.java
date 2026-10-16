package com.yupi.model.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

@Getter
public enum ReportReasonEnum {

    VIOLENCE("暴力", "violence"),
    GANG("涉黑", "gang"),
    PORN("色情", "porn"),
    ILLEGAL("违法", "illegal"),
    OTHER("其他", "other");

    private final String text;
    private final String value;

    ReportReasonEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static ReportReasonEnum getByValue(String value) {
        if (ObjectUtil.isEmpty(value)) {
            return null;
        }
        for (ReportReasonEnum e : ReportReasonEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
