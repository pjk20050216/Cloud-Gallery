package com.yupi.configuration;

import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

// 请求包装过滤器：1、对 JSON 格式数据进行反序列化
@Order(-1) // 优先级注解（值越小，优先级越高），表示这个过滤器在过滤器链中排在靠前
@Component // 保证这个类是一个全局配置类
public class HttpRequestWrapperFilter implements Filter {

    // 用于这个是在 Spring 容器中，所以每当是发送 JSON 格式数据的 http 请求就会调用这个方法
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request instanceof HttpServletRequest) { // 确保当前是一个 Http 请求
            HttpServletRequest servletRequest = (HttpServletRequest) request;
            String contentType = servletRequest.getHeader(Header.CONTENT_TYPE.getValue());
            if (ContentType.JSON.getValue().equals(contentType)) {
                // 如果是 json 格式的则直接使用我们自己的
                chain.doFilter(new RequestWrapper(servletRequest), response);
            } else {
                chain.doFilter(request, response);
            }
        }
    }
}

