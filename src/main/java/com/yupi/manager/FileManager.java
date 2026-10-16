package com.yupi.manager;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.qcloud.cos.COSClient;

import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.yupi.common.ResultUtils;
import com.yupi.configuration.CosClientConfig;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
@Deprecated
public class FileManager {
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    @Resource
    private COSClient cosClient;

    // 文件形式上传并且检验，并且返回文件的各种信息
    public UploadPictureResult uploadPictureResult(MultipartFile multipartFile, String uploadPathPrefix) {
        // 1、校验图片
        validPicture(multipartFile);
        // 2、图片上传地址，就是对原始文件路径加一堆修饰
        String randomString = RandomUtil.randomString(16);

        String originalFilename = multipartFile.getOriginalFilename();

        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), randomString,
                FileUtil.getSuffix(originalFilename));

        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);

        File file = null;
        try {
            // 1、创建一个本地临时文件
            file = File.createTempFile(uploadPath, null); // 其中 uploadPath 为在本地创建临时文件的路径
            multipartFile.transferTo(file); // 将前端的参数转到临时文件中

            // 2、将这个临时文件上传到腾讯云服务器
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

            // 3、获取图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 4、封装返回结果
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            double picScale = NumberUtil.round(picWidth + 1.0 / picHeight, 2).doubleValue();

            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + '/' + uploadPath);
            uploadPictureResult.setName(FileUtil.mainName(originalFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());

            return uploadPictureResult;
        } catch (IOException e) {
            log.error("文件上传到对象储存失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            this.deleteTempleFile(file);
        }
    }

    // url 上传图片
    public UploadPictureResult uploadPictureByUrl(String url, String uploadPathPrefix) {
        // 1、校验图片
        validPicture(url);
        // 2、图片上传地址，就是对原始文件路径加一堆修饰
        String randomString = RandomUtil.randomString(16);

        String originalFilename = FileUtil.mainName(url);

        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), randomString,
                FileUtil.getSuffix(originalFilename));

        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);

        File file = null;
        try {
            // 1、创建一个本地临时文件
            file = File.createTempFile(uploadPath, null); // 其中 uploadPath 为在本地创建临时文件的路径
            HttpUtil.downloadFile(url, file); // 通过 url 将文件传到 file 对象中
            // 2、将这个临时文件上传到腾讯云服务器
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

            // 3、获取图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 4、封装返回结果
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            double picScale = NumberUtil.round(picWidth + 1.0 / picHeight, 2).doubleValue();

            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + '/' + uploadPath);
            uploadPictureResult.setName(FileUtil.mainName(originalFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());

            return uploadPictureResult;
        } catch (IOException e) {
            log.error("文件上传到对象储存失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            this.deleteTempleFile(file);
        }
    }

    // 通过 url 校验图片
    private void validPicture(String url) {
        // 1、文件地址校验
        ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.PARAMS_ERROR, "文件地址为空");

        // 2、文件格式校验
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不对");
        }
        // 3、文件协议校验
        ThrowUtils.throwIf(!url.startsWith("http://") && !url.startsWith("https://"),
                ErrorCode.PARAMS_ERROR,
                "仅支持 http 或 https 协议");


        HttpResponse httpResponse = null;
        try {
            // 4、通过 Head 请求判断这个 url 地址是否能够请求到内容，就是判断请求状态
            httpResponse = HttpUtil.createRequest(Method.HEAD, url).execute();
            // 判断请求状态知否为：200
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 5、文件存在，然后进行类型校验
            String contentType = httpResponse.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 6. 校验文件大小
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_MAX = 1024 * 1024; // 这个表示 1MB
                    ThrowUtils.throwIf(contentLength > 2 * ONE_MAX, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        } finally {
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    // 通过文件形式校验图片
    private void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件为空");
        // 1、校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_MAX = 1024 * 1024; // 这个表示 1MB
        ThrowUtils.throwIf(fileSize > 10 * ONE_MAX, ErrorCode.PARAMS_ERROR, "文件大小大于 10 MB");

        // 2、校验文件格式，就是后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许上传文件的后缀列表
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "png", "jpg", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件后缀错误");
    }

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
