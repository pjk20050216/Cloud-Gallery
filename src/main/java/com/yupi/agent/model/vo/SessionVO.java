package com.yupi.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 会话
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionVO implements Serializable {

    private Long id;

    private String title;

    private String agentType;

    private Integer messageCount;

    private Date lastMessageTime;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
