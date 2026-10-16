package com.yupi.manager;


import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.yupi.configuration.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// 使用了腾讯云数据万象（CI）的功能，主要实现了WebP 实时转码和缩略图压缩，这通常是为了配合 CDN 进行更高效的图片分发
@Component
public class CosManager {
    @Resource
    private CosClientConfig cosClientConfig;

    // 这个是腾讯云自带
    @Resource
    private COSClient cosClient;

    // 1、上传本地文件到 COS
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest =
                new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    // 2、获取取文件到本地服务器中
    public COSObject getCosClient(String key) { // key 指的文件在COS存储桶中的路径和名称
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    // 3、上传图片到 COS 并且上传时候解析图片
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest =
                new PutObjectRequest(cosClientConfig.getBucket(), key, file);

        // 获取图片的基本信息
        PicOperations picOperations = new PicOperations();
        // 1、表示返回原图信息
        picOperations.setIsPicInfo(1);

        // 2、创建一个压缩规则的数组
        List<PicOperations.Rule> rules = new ArrayList<>();

        // 3、对图片进行压缩（转为 webp 格式）
        String webpKey = FileUtil.mainName(key) + ".webp";

        // 4、图片压缩的规则
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webpKey);
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setRule("imageMogr2/format/webp");
        rules.add(compressRule);

        // 5、缩略图处理规则，并且仅对 20 KB 大小的图片生效（防止适得其反）
        if (file.length() > 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            // 拼接缩略图的路径
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail" + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128)); // 这里设置压缩图的大小
            rules.add(thumbnailRule);
        }

        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }

    // 4、删除 COS 中的对象
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
}
