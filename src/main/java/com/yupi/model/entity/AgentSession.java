package com.yupi.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 智能体会话
 */
@TableName(value = "agent_session")
@Data
public class AgentSession implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 当前智能体
     */
    private String agentType;

    /**
     * 历史摘要（长对话压缩用）
     */
    private String summary;

    /**
     * 结构化记忆 JSON（关键实体：图片/空间/任务 id 等）
     */
    private String memory;

    /**
     * 已压缩到的消息 id 水位，避免重复压缩同一批消息
     */
    private Long compressedUntilId;

    /**
     * 消息条数
     */
    private Integer messageCount;

    /**
     * 最后消息时间
     */
    private Date lastMessageTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
