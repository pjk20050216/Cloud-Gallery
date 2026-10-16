package com.yupi.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;


// 空间级别，用于展示给用户，不同的空间储存
@Data
@AllArgsConstructor
public class SpaceLevel {

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}
