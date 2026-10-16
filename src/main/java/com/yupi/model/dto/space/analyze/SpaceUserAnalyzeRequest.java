package com.yupi.model.dto.space.analyze;

import lombok.Data;
import lombok.EqualsAndHashCode;

// 根据图片创建时间分类
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUserAnalyzeRequest extends SpaceAnalyzeRequest {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 时间维度：day / week / month
     */
    private String timeDimension;
}

