package com.yupi.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * SSE 事件。
 * <p>
 * type 取值：
 * <ul>
 *     <li>agent_switch  切换到某个智能体</li>
 *     <li>tool_call     即将调用工具</li>
 *     <li>tool_result   工具执行完成</li>
 *     <li>delta         文本增量</li>
 *     <li>done          本轮结束</li>
 *     <li>error         发生错误</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEventVO implements Serializable {

    private String type;

    private Long sessionId;

    private String agent;

    private String displayName;

    private String tool;

    private String toolCallId;

    private Object args;

    private Boolean success;

    private String summary;

    private String content;

    private Long messageId;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer code;

    private String message;

    /**
     * 高危操作确认令牌（仅 confirm_required 事件有值）
     */
    private String confirmToken;

    private static final long serialVersionUID = 1L;
}
