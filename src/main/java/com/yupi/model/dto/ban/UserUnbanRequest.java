package com.yupi.model.dto.ban;

import lombok.Data;

import java.io.Serializable;

/**
 * 解封请求（管理端）
 */
@Data
public class UserUnbanRequest implements Serializable {

    private Long banRecordId;

    private String unbanReason;

    private static final long serialVersionUID = 1L;
}
