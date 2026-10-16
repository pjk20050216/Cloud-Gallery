package com.yupi.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("token_usage_record")
public class TokenUsageRecord {
    /**
     * id
     */
    private Long id;
    
    /**
     * 空间 id
     */
    private Long spaceId;
    
    /**
     * 用户 id
     */
    private Long userId;
    
    /**
     * 消耗的token数量
     */
    private Long tokenCount;
    
    /**
     * API类型：outPainting/imageGeneration等
     */
    private String apiType;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 创建时间
     */
    private Date createTime;
}
