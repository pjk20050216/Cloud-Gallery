package com.yupi.manager.auth.annotation;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import com.yupi.aop.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

// sa-token 开启注解配置，这个 controller 包中带有 @Auth 注解的接口就会进行权限校验了

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    // 注册 sa-token 拦截器，打开注解式鉴权功能
    //
    // ⚠️ 这里必须写 "/**" 而不是 "/api/**"：
    // 项目的 server.servlet.context-path 是 /api，而拦截器路径是**相对 context-path** 匹配的
    // （Spring 用 getLookupPathForRequest 取 servlet 内路径，不含 context path）。
    // 写成 "/api/**" 会导致拦截器永远不匹配，所有 @SaCheckLogin / @SaSpaceCheckPermission
    // 注解形同虚设——这是一个真实存在过的鉴权漏洞。
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 智能体对话前端（静态资源）
                        "/agent-ui/**"
                );
    }

    @PostConstruct
    public void rewriteSaStrategy(){
        // 重写 sa-token 的注解处理器，增加注解合并功能
        SaAnnotationStrategy.instance.getAnnotation = (element, annotationClass) ->
        {
            return AnnotatedElementUtils.getMergedAnnotation(element, annotationClass);
        };
    }
}

