package com.yupi.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 异步任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskVO implements Serializable {

    private String taskId;

    private Long sessionId;

    private String type;

    private String status;

    private Integer progress;

    private String progressMessage;

    /**
     * 结果（成功时是模型回答或统计信息）
     */
    private Object output;

    private String errorMsg;

    private Date createTime;

    private Date startTime;

    private Date endTime;

    private static final long serialVersionUID = 1L;
}
