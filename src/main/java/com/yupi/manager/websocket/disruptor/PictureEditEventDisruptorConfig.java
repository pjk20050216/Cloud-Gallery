package com.yupi.manager.websocket.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

// 图片编辑事件 Disruptor 配置，基于 disruptor 框架
@Configuration
public class PictureEditEventDisruptorConfig {

    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    @Bean("pictureEditEventDisruptor") // 这个是 Bean 的名称
    public Disruptor<PictureEditEvent> messageModelRingBuffer() {
        // 配置 Disruptor 内部环形数组 ringBuffer 的大小
        int bufferSize = 1024 * 256;
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                PictureEditEvent::new,
                // 事件工厂，这是一个 Lambda 表达式，等价于 () -> new PictureEditEvent()。
                bufferSize,
                ThreadFactoryBuilder.create()
                        .setNamePrefix("pictureEditEventDisruptor").build()
        );
        // 设置消费者
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        // 开启 disruptor
        disruptor.start();
        return disruptor;
    }
}

