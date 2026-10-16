package com.yupi.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yupi.manager.websocket.disruptor.PictureEditEvent;
import com.yupi.manager.websocket.disruptor.PictureEditEventProducer;
import com.yupi.manager.websocket.model.*;
import com.yupi.model.entity.User;
import com.yupi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 图片编辑 WebSocket 处理器
@Slf4j
@Component
public class PictureEditHandler extends TextWebSocketHandler {

    /**
     * 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
     */
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    /**
     * 保存所有连接的会话，key: pictureId, value: 用户会话集合
     */
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 某张图片的编辑记录，key: pictureId, value: 编辑记录，用于某个用户中途加入编辑的时候看到最新的编辑记录
     */
    private final Map<Long, Set<TextMessage>> pictureEditRecodes = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    // 连接建立成功
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 初始化 pictureSessions 中的 Set<TextMessage>
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        // 然后根据 pictureId 向集合中添加数据
        pictureSessions.get(pictureId).add(session);
        // 构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给所有用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);

    }

    // 根据前端传来消息，根据消息类别处理消息
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 获取消息内容，将 JSON 消息解析为 PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);

        // 从 Session 属性中获取公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        PictureEditContext pictureEditContext = new PictureEditContext(pictureEditRequestMessage, session, user, pictureId);
        // 根据消息类型处理消息（生产消息到 Disruptor 环型队列中）
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, pictureEditContext);

    }

    // 进入编辑状态
    public void handleEnterEditMessage(PictureEditEvent pictureEditEvent) throws Exception {
        User user = pictureEditEvent.getUser();
        Long pictureId = pictureEditEvent.getPictureId();
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给所有用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
        // 这个位置是啥呀？
        if (!Objects.equals(pictureEditingUsers.get(pictureId), user.getId())) {
            handleEditErrorMessage(user, pictureId, pictureEditEvent.getSession());
        }
    }

    // 处理编辑操作
    public void handleEditActionMessage(PictureEditEvent pictureEditEvent) throws Exception {
        PictureEditRequestMessage pictureEditRequestMessage = pictureEditEvent.getPictureEditRequestMessage();
        WebSocketSession session = pictureEditEvent.getSession();
        User user = pictureEditEvent.getUser();
        Long pictureId = pictureEditEvent.getPictureId();
        // 获取当前正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 一样的，构造响应
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s 执行 %s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    // 退出编辑状态
    public void handleExitEditMessage(PictureEditEvent pictureEditEvent) {
        Long pictureId = null;
        try {
            User user = pictureEditEvent.getUser();
            pictureId = pictureEditEvent.getPictureId();
            Long editingUserId = pictureEditingUsers.get(pictureId);
            if (editingUserId != null && editingUserId.equals(user.getId())) {
                // 构造响应，发送退出编辑的消息通知
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
                String message = String.format("%s退出编辑图片", user.getUserName());
                pictureEditResponseMessage.setMessage(message);
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                broadcastToPicture(pictureId, pictureEditResponseMessage);
                // 移除当前用户的编辑状态
                pictureEditingUsers.remove(pictureId);
            }
            pictureSessions.get(pictureId).remove(pictureEditEvent.getSession());

        } catch (Exception e) {
            log.error("PictureEditHandler#handleExitEditMessage {}", ExceptionUtils.getRootCauseMessage(e));
            throw new RuntimeException(e);
        } finally {
            // 对应图片没人操作那么就删除编辑记录，防止内存泄露
            if (pictureSessions.get(pictureId).isEmpty()) {
                pictureEditRecodes.remove(pictureId);
            }
        }
    }

    // 关闭连接时也要释放资源
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        // 从 session 中获取公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        PictureEditEvent pictureEditEvent = new PictureEditEvent(null, session, user, pictureId);
        // 移除用户的编辑状态
        handleExitEditMessage(pictureEditEvent);
        // 删除会话，就是删除 Set 集合中对应的值
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if(sessionSet != null){
            sessionSet.remove(session);
            if(sessionSet.isEmpty()){
                pictureSessions.remove(pictureId);
            }
        }
        // 同样也要发送响应，通知其他用户
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    // 用于提示错误信息的
    private void handleEditErrorMessage(User user, Long pictureId, WebSocketSession sendSession) throws Exception {
        // 没有权限
        Long editUserId = pictureEditingUsers.get(pictureId);
        User editUser = userService.getById(editUserId);
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
        pictureEditResponseMessage.setMessage(String.format("操作失败 %s 正在操作", editUser.getUserName()));
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToOneUser(pictureId, Collections.singletonList(getTextMessage(pictureEditResponseMessage)), sendSession);
    }

    // 用于向“其他”用户显示有人在操作图片
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage,
                                    WebSocketSession excludeSession) throws Exception {
        // 根据图片 Id 获取会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 配置 ObjectMapper，用于转换数据为 JSON 格式，避免精度丢失
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化。将 Long 类型转为 String
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);

            // 序列化为 JSON 字符串
            String str = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(str);
            for (WebSocketSession session : sessionSet) {
                // 排除不需要发送的，指用户本人
                if(excludeSession != null && session.equals(excludeSession))
                {
                    continue;
                }
                if (session.isOpen()) { // 判断这个会话是否开启
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    // 用于向所有用户显示有人在操作图片
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }


    private void broadcastToOneUser(Long pictureId, List<TextMessage> textMessages, WebSocketSession sendSession) throws Exception {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            if (Objects.isNull(textMessages) || textMessages.isEmpty()) {
                return;
            }
            // 创建 ObjectMapper
            textMessages.forEach(textMessage -> {
                for (WebSocketSession session : sessionSet) {
                    // 给指定用户发送信息
                    if (session.isOpen() && session.equals(sendSession)) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            log.error("PictureEditHandler#broadcastToOneUser {}", ExceptionUtils.getRootCauseMessage(e));
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        }
    }

    /**
     * 将图片编辑响应消息转换为文本消息
     * @param pictureEditResponseMessage 图片编辑响应消息对象
     * @return 转换后的文本消息对象
     * @throws JsonProcessingException JSON处理异常
     */
    private TextMessage getTextMessage(PictureEditResponseMessage pictureEditResponseMessage) throws JsonProcessingException {
        // 创建 ObjectMapper 实例，用于 JSON 序列化和反序列化
        ObjectMapper objectMapper = new ObjectMapper();
        // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        // 支持 long 基本类型
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        // 序列化为 JSON 字符串
        String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
        return new TextMessage(message);
    }
}









