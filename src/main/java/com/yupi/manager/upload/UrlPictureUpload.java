package com.yupi.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;


@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String url = (String) inputSource;
        HttpUtil.downloadFile(url, file); // 通过 url 将文件传到 file 对象中
    }

    @Override
    protected String getOriginalFilename(Object inputSource) {
        String url = (String) inputSource;
        return FileUtil.mainName(url);
    }

    @Override
    protected void validPicture(Object inputSource) {
        String url = (String) inputSource;
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
}
