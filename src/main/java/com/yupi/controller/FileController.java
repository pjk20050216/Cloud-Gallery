package com.yupi.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.yupi.annotation.AutoCheck;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.manager.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private CosManager cosManager;

    // 1、测试文件上传
    @AutoCheck(mustRole = "admin")
    @PostMapping("/test/upload")
    public BaseResponse<String> upload(@RequestPart("file") MultipartFile multipartFile) {
        // @RequestPart：将 HTTP 请求中的名为 "file" 的部分绑定到 MultipartFile 参数上

        // 这是一个文件上传的完整代码，可以复制粘贴到别的代码中
        String fileName = multipartFile.getOriginalFilename();
        String filePath = String.format("/test/%s", fileName); // 相当于文件上传到前端时，路径前面要加：/test/
        File file = null;
        try {
            // 1、创建一个本地临时文件
            file = File.createTempFile(filePath, null); // 转一下文件的格式
            multipartFile.transferTo(file);

            // 2、将这个临时文件上传到腾讯云服务器
            cosManager.putObject(filePath, file);

            // 3、返回可以访问的地址
            return ResultUtils.success(filePath);
        } catch (IOException e) {
            log.error("file upload error，file path = {}", filePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (file != null) {
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error，filePath = {}", filePath);
                }
            }
        }
    }

    // 2、测试文件下载
    @AutoCheck(mustRole = "admin")
    @GetMapping("/test/download")
    /*
     这里的 filePath 指的文件在 COS 中的路径
     从COS下载文件时，COS会通过这条 “ 输入流 ” 把文件数据一点一点地传输到你的程序中
    */
    public void download(String filePath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInput = null; // 创建一个输入流，所以每次使用时得初始化
        try {
            // 获取 COS 对象
            COSObject cosObject = cosManager.getCosClient(filePath);
            cosObjectInput = cosObject.getObjectContent();
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);

            // 设置响应头，让浏览器知道用户要干什么
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filePath);

            // 向响应头中加入操作
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("downLoad error，filepath:{}", filePath, e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "下载失败");
        } finally {
            // 释放流
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }
}
