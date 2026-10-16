package com.yupi.manager.websocket.disruptor;

import cn.hutool.json.JSONUtil;
import com.lmax.disruptor.WorkHandler;
import com.yupi.manager.websocket.PictureEditHandler;
import com.yupi.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yupi.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.manager.websocket.model.PictureEditResponseMessage;
import com.yupi.model.entity.User;
import com.yupi.service.UserService;
import groovy.lang.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

// 图片编辑事件处理器（消费者），源于 disruptor 框架
@Slf4j
@Component
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    @Lazy
    private PictureEditHandler pictureEditHandler;

    @Resource
    private UserService userService;

    @Override
    public void onEvent(PictureEditEvent event) throws Exception {
        PictureEditRequestMessage pictureEditRequestMessage = event.getPictureEditRequestMessage();
        WebSocketSession session = event.getSession();
        User user = event.getUser();
        Long pictureId = event.getPictureId();
        PictureEditEvent pictureEditEvent = new PictureEditEvent(pictureEditRequestMessage, session, user, pictureId);
        // 获取到消息类别
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);

        // 调用对应的消息处理方法
        switch (pictureEditMessageTypeEnum) {
            case ENTER_EDIT:
                pictureEditHandler.handleEnterEditMessage(pictureEditEvent);
                break;
            case EDIT_ACTION:
                pictureEditHandler.handleEditActionMessage(pictureEditEvent);
                break;
            case EXIT_EDIT:
                pictureEditHandler.handleExitEditMessage(pictureEditEvent);
                break;
            default:
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
                break;
        }
    }
}

