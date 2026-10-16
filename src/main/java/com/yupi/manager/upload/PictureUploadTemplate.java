package com.yupi.manager.upload;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.yupi.configuration.CosClientConfig;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.manager.CosManager;
import com.yupi.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

// 对文件上传中的方法进行抽取，形成抽象方法便于直接调用（模板方法的核心实现代码）

@Slf4j
public abstract class PictureUploadTemplate {
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    @Resource
    private COSClient cosClient;

    // 文件形式上传并且检验，并且返回文件的各种信息
    public UploadPictureResult uploadPictureResult(Object inputSource, String uploadPathPrefix) {
        // 1、校验图片
        validPicture(inputSource);
        // 2、图片上传地址，就是对原始文件路径加一堆修饰
        String randomString = RandomUtil.randomString(16);

        String originalFilename = getOriginalFilename(inputSource);

        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), randomString,
                FileUtil.getSuffix(originalFilename));

        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);

        File file = null;
        try {
            // 1、创建一个本地临时文件
            file = File.createTempFile(uploadPath, null); // 其中 uploadPath 为在本地创建临时文件的路径

            processFile(inputSource, file); // 将前端的参数转到临时文件中

            // 2、将这个临时文件上传到腾讯云服务器
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

            // 3、获取原图图片信息对象，不压缩，用于用户下载
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 3、获取原图图片压缩后的对象
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList(); // 一个个处理后的图片对象列表
            if (CollUtil.isNotEmpty(objectList)) {
                // 1、获取压缩后的图片对象
                CIObject compressedCiObject = objectList.get(0);
                // 2、获取缩略图的图片对象
                CIObject thumbnailCiObject = compressedCiObject; // 默认值
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1);
                }

                // 封装压缩图的返回结果
                return buildResult(compressedCiObject, originalFilename, thumbnailCiObject, imageInfo);
            }
            // 4、封装返回结果
            return buildResult(imageInfo, uploadPath, originalFilename, file);
        } catch (Exception e) {
            log.error("文件上传到对象储存失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            this.deleteTempleFile(file);
        }
    }

    // 压缩图片的封装返回结果
    private UploadPictureResult buildResult(CIObject ciObject, String originalFilename, CIObject thumbnailCiObject,
                                            ImageInfo imageInfo) {
        int picWidth = ciObject.getWidth();
        int picHeight = ciObject.getHeight();
        double picScale = NumberUtil.round(picWidth + 1.0 / picHeight, 2).doubleValue();

        // 封装返回前端的结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        // 设置压缩图地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + ciObject.getKey());
        uploadPictureResult.setName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(ciObject.getSize().longValue());
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(ciObject.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());
        // 设置缩略图地址
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());

        return uploadPictureResult;
    }

    // 不获取压缩图片的封装返回结果
    private UploadPictureResult buildResult(ImageInfo imageInfo, String uploadPath, String originalFilename, File file) {
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth + 1.0 / picHeight, 2).doubleValue();

        // 封装返回前端的结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(cosClientConfig.getHost() + '/' + uploadPath);
        uploadPictureResult.setName(FileUtil.mainName(originalFilename));
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());

        return uploadPictureResult;
    }

    // 模板方法：定义了下面三个抽象方法，然后由具体实现类来实现
    // 1、将不同类型的输入源转换为统一的临时文件，为后续上传做准备。
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    protected abstract String getOriginalFilename(Object inputSource);

    // 对输入源进行校验，判断上传的图片是否符合要求
    protected abstract void validPicture(Object inputSource);

    // 清理临时文件
    private void deleteTempleFile(File file) {
        if (file != null) {
            return;
        }

        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error，filePath = {}", file.getAbsolutePath());
        }
    }


}
