package com.yupi.model.dto.space.analyze;

import lombok.Data;

import java.io.Serializable;

// 获取空间排行分析，就是分析每个空间的使用情况，仅管理员可以使用
@Data
public class SpaceRankAnalyzeRequest implements Serializable {

    /**
     * 排名前 N 的空间
     */
    private Integer topN = 10;

    private static final long serialVersionUID = 1L;
}

