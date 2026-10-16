package com.yupi.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

// 图片上传请求

@Data
public class PictureUploadRequest implements Serializable {
    private Long id;

    private String url;

    // 图片名称
    private String picName;

    // 图片空间 Id
    private Long spaceId;

    private final static long serialVersion = 1L;
}
