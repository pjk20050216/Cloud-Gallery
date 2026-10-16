package com.yupi.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.yupi.manager.websocket.model.PictureEditContext;
import com.yupi.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

// 图片编辑事件（Producer 发布者），基于 disruptor 框架
@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    // 发布事件，也就是向 disruptor 环型缓存区中放置任务
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, PictureEditContext pictureEditContext) {
        // 1、获取缓存区对象
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 2、获取可用的位置
        long next = ringBuffer.next();
        // 3、然后存放事件
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setSession(pictureEditContext.getSession());
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setUser(pictureEditContext.getUser());
        pictureEditEvent.setPictureId(pictureEditContext.getPictureId());
        // 发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void close() {
        pictureEditEventDisruptor.shutdown();
    }
}

