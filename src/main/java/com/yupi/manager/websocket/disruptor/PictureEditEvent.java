package com.yupi.manager.websocket.disruptor;

import com.yupi.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.model.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.checkerframework.checker.units.qual.A;
import org.springframework.web.socket.WebSocketSession;

// 图片编辑事件
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PictureEditEvent {

    /**
     * 消息
     */
    private PictureEditRequestMessage pictureEditRequestMessage;

    /**
     * 当前用户的 session
     */
    private WebSocketSession session;
    
    /**
     * 当前用户
     */
    private User user;

    /**
     * 图片 id
     */
    private Long pictureId;

}

