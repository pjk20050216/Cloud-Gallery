package com.yupi.api.imageSearch.model;

import lombok.Data;

// 图片搜索结果，就是通过搜索查看显示图片
@Data
public class  ImageSearchResult {

    // 缩略图地址，就是相似图地址
    private String thumbUrl;

    // 这些缩略图的来源地址
    private String fromUrl;

}
