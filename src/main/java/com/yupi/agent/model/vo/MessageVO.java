package com.yupi.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 历史消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO implements Serializable {

    private Long id;

    private String role;

    private String agentType;

    private String content;

    private String toolName;

    private String status;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
