package com.yupi.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;


// 图片编辑请求
@Data
public class PictureEditByBatchRequest implements Serializable {

    /**
     * 图片 id 列表
     */
    private List<Long> pictureIdList;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

    // 再是命名的规则
    private String nameRule;

    private static final long serialVersionUID = 1L;
}

