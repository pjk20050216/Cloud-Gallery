package com.yupi.api.imageSearch.sub;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

// 第一步：获取以图搜图的地址
@Slf4j
public class GetImagePageUrlApi {

    public static String getImagePageUrl(String imageUrl) {
        // 1、准备请求参数
        Map<String, Object> formData = new HashMap<>();
        // 下面的内容是通过 网络请求F12 在浏览器中看到的
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        // 2、获取当前时间戳
        long uptime = System.currentTimeMillis();
        // 3、获取搜图地址
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;

        try {
            // 4、发送这个请求，获取对应的相似图片
            HttpResponse httpResponse = HttpRequest.post(url)
                    .form(formData)
                    .timeout(5000) // 请求不超过 5 秒
                    .execute();
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // 响应正常则解析响应

            String body = httpResponse.body(); // 这个里面含有 status、mag、data 三个内容
            Map<String, Object> result = JSONUtil.toBean(body, Map.class); // 将响应体解析为一个 Map 集合
            Integer i = (Integer) result.get("status");
            if (result == null || !Integer.valueOf(1002).equals(result.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            // todo 这里 data 找不到数据
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String rawUrl = (String) data.get("url"); // 这个原生的 url 可能会有些问题
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);// 因此对 url 进行解码
            if (StrUtil.isBlank(searchResultUrl)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效结果地址");
            }
            return searchResultUrl;
        } catch (Exception e) {
            log.error("调用百度以图搜图接口失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    // 测试获取搜到相似图片的页面
    public static void main(String[] args) {
        String imageUrl = "https://image2url.com/images/1766236256988-bda25426-3f27-4ef0-8a40-96b07824b769.jpg";
        System.out.println("测试：" + getImagePageUrl(imageUrl));
    }
}