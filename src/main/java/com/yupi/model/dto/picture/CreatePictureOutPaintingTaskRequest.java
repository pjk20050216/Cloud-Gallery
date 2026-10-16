package com.yupi.model.dto.picture;

import com.yupi.api.aliYunAi.model.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serializable;

// 实现的扩图请求

@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 扩图参数
     */
    private CreateOutPaintingTaskRequest.Parameters parameters;

    private static final long serialVersionUID = 1L;
}

