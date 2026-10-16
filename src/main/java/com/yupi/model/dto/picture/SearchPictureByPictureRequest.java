package com.yupi.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

// 百度以图搜图的请求
@Data
public class SearchPictureByPictureRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}

