package com.yupi.model.vo;

import lombok.Data;

import java.util.List;

// 图片标签列表分类视图
@Data
public class PictureTagCategory {

    private List<String> tagList;

    private List<String> categoryList;

}
