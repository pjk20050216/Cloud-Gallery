package com.yupi.agent.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 高危操作确认请求
 */
@Data
public class ConfirmRequestDTO implements Serializable {

    /**
     * 确认令牌（由 SSE 的 confirm_required 事件下发）
     */
    private String confirmToken;

    /**
     * true=同意执行，false=拒绝
     */
    private Boolean approve;

    private static final long serialVersionUID = 1L;
}
