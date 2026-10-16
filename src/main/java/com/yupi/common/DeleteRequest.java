package com.yupi.common;

import lombok.Data;

import java.io.Serializable;

// 用于执行通用的删除请求

@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}

