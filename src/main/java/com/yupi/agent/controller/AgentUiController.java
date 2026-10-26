package com.yupi.agent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 智能体对话前端的入口跳转。
 * <p>
 * 静态资源在 {@code classpath:/static/agent-ui/}，
 * 但 Spring Boot 的欢迎页只对根路径生效，访问 {@code /agent-ui} 或 {@code /agent-ui/}
 * 不会自动找到 index.html，所以这里做一次跳转。
 */
@Controller
public class AgentUiController {

    @GetMapping({"/agent-ui", "/agent-ui/"})
    public void index(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/agent-ui/index.html");
    }
}
