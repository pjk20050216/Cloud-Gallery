package com.yupi.model.dto.file;

import lombok.Data;

// 上传图片结果后的封装类
@Data
public class UploadPictureResult {
    // 1、图片地址：腾讯云域名 + 修饰后的图片下载地址
    private String url;

    // 缩略图 url
    private String thumbnailUrl;

    // 2、图片名称
    private String name;

    private String picColor;

    // 3、文件体积
    private Long picSize;

    // 4、图片宽度
    private int picWidth;

    // 5、图片高度
    private int picHeight;

    // 6、图片的宽高比
    private Double picScale;

    // 7、图片格式
    private String picFormat;

}
