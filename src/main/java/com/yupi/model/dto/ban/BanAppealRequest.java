package com.yupi.model.dto.ban;

import lombok.Data;

import java.io.Serializable;

/**
 * 申诉提交请求（用户端）
 */
@Data
public class BanAppealRequest implements Serializable {

    private Long banRecordId;

    /**
     * 申诉理由
     */
    private String content;

    private static final long serialVersionUID = 1L;
}
