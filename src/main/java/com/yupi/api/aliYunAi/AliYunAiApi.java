package com.yupi.api.aliYunAi;


import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.yupi.api.aliYunAi.model.CreateOutPaintingTaskRequest;
import com.yupi.api.aliYunAi.model.CreateOutPaintingTaskResponse;
import com.yupi.api.aliYunAi.model.GetOutPaintingTaskResponse;
import com.yupi.exception.BusinessException;
import com.yupi.exception.ErrorCode;
import com.yupi.exception.ThrowUtils;
import com.yupi.model.entity.SpaceUser;
import com.yupi.service.SpaceService;
import com.yupi.service.SpaceUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;


@Slf4j
@Component
public class AliYunAiApi {

    // 读取配置文件
    @Value("${aliYunOssAi.apiKey}")
    private String apiKey;

    // API 调用消耗的 token
    @Value("${aliYunOssAi.tokenCost")
    private Integer tokenCost;

    // 因为这个 AI 调用消耗的 token 是与会员空间有关的
    @Resource
    private SpaceUserService spaceUserService;

    // 请求阿里云 AI 扩图服务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询 AI 扩图的任务状态，%s 为一个等会占位用的
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    // 创建任务
    public CreateOutPaintingTaskResponse createOutPaintingTask(Long userId, CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        ThrowUtils.throwIf(createOutPaintingTaskRequest == null, ErrorCode.NOT_FOUND_ERROR, "扩图参数为空");
        // 检查用户额度是否充足
        SpaceUser spaceUser = spaceUserService.getById(userId);
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR, "用户空间不存在");
        ThrowUtils.throwIf(spaceUser.getRemainingTokens() < tokenCost, ErrorCode.OPERATION_ERROR,
                "您的API调用额度不足，请升级会员或充值");

        HttpRequest request = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("X-DashScope-Async", "enable") // 开启异步处理
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest)); // 将请求数据转为 JSON 发送给阿里云百炼
        try (HttpResponse response = request.execute()) { // 这种在 try 头里面释放资源的方式得，但这个对象得实现 AutoCloseable 接口
            if (!response.isOk()) {
                log.error("请求异常：{}", response.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            // 将请求后的响应转为响应类
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(response.body(), CreateOutPaintingTaskResponse.class);
            // Code为错误码
            if (createOutPaintingTaskResponse.getCode() != null) {
                String message = createOutPaintingTaskResponse.getMessage();
                log.error("请求异常：{}", message);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败" + message);
            }

            // API 调用成功，扣除用户额度
            boolean deductSuccess = spaceUserService.deductTokens(userId, tokenCost);
            if (!deductSuccess) {
                log.error("扣除用户{}的{}个token失败", userId, tokenCost);
            }

            return createOutPaintingTaskResponse;
        }
    }

    /**
     * 查询创建的任务
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 id 不能为空");
        }
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpGet httpGet = new HttpGet(String.format(GET_OUT_PAINTING_TASK_URL, taskId));
            httpGet.addHeader("Authorization", "Bearer " + apiKey);

            CloseableHttpResponse response = httpClient.execute(httpGet);

            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            if (response.getStatusLine().getStatusCode() != 200) {
                log.error("请求异常：{}", responseBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败");
            }
            return JSONUtil.toBean(responseBody, GetOutPaintingTaskResponse.class);
        } catch (IOException e) {
            log.error("获取图片 task 信息发生错误", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统错误，请稍后再试");
        }
    }

}


