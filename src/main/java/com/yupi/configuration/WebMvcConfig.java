package com.yupi.configuration;

import com.yupi.aop.UserBanInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * 拦截器注册。
 * <p>
 * 注意：项目的 context-path 是 /api，所以这里的路径模式是**相对 context-path** 的，
 * 应写 {@code /**} 而不是 {@code /api/**}。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private UserBanInterceptor userBanInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userBanInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 被封禁用户仍然可以登录、登出、查看自己是谁
                        "/user/login",
                        "/user/logout",
                        "/user/get/login",
                        // 以及通过智能体询问"我为什么被封"
                        "/agent/**",
                        // 智能体对话前端（静态资源，被封用户也要能打开）
                        "/agent-ui/**",
                        // 自己的处罚记录与申诉（智能体走的是 /agent/**，这里给前端留直连）
                        "/user/ban/my",
                        "/user/ban/appeal",
                        // 错误页
                        "/error"
                );
    }
}
