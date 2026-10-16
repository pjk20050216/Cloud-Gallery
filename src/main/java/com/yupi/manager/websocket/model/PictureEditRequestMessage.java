package com.yupi.manager.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 图片编辑请求消息
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditRequestMessage {

    /**
     * 消息类型，例如 "ENTER_EDIT"（进入编辑）, "EXIT_EDIT"（退出编辑）, "EDIT_ACTION"（编辑操作）
     */
    private String type;

    /**
     * 执行的编辑动作（放大，缩小）
     */
    private String editAction;
}

