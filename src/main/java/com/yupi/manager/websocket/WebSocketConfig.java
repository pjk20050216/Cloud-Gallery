package com.yupi.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

// WebSocketConfig 配置（定义连接）
@Configuration
@EnableWebSocket // 通过这个注解就可以启动这个 WebSocket 服务了
public class WebSocketConfig implements WebSocketConfigurer {

    // 定义处理器
    @Resource
    private PictureEditHandler pictureEditHandler;

    // 定义拦截器
    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOrigins("*"); // 跨域的范围
    }
}
