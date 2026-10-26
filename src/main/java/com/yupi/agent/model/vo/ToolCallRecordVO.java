package com.yupi.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 一次工具调用记录（用于非流式接口的返回值）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRecordVO implements Serializable {

    private String toolCallId;

    private String tool;

    private Object args;

    private boolean success;

    private String summary;

    private long costMs;

    private static final long serialVersionUID = 1L;
}
