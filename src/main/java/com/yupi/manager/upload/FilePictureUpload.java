package com.yupi.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// 文件图片上传（也是抽象方法模式中的三个抽象方法的具体实现类）
@Service
public class FilePictureUpload extends PictureUploadTemplate {

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }

    @Override
    protected String getOriginalFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
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
}
