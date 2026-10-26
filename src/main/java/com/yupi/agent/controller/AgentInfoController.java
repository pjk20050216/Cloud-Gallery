package com.yupi.agent.controller;

import com.yupi.agent.agent.SubAgent;
import com.yupi.agent.agent.SubAgentRegistry;
import com.yupi.agent.model.vo.AgentInfoVO;
import com.yupi.common.BaseResponse;
import com.yupi.common.ResultUtils;
import com.yupi.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体能力清单。
 * <p>
 * 前端可以用它渲染"我能帮你做什么"的引导语，也方便调试时确认路由配置。
 */
@RestController
@RequestMapping("/agent")
public class AgentInfoController {

    @Resource
    private SubAgentRegistry subAgentRegistry;

    @Resource
    private UserService userService;

    @GetMapping("/agents")
    public BaseResponse<List<AgentInfoVO>> listAgents(HttpServletRequest request) {
        // 需要登录才能查看
        userService.getLoginUser(request);

        List<AgentInfoVO> list = subAgentRegistry.all().stream()
                .map(agent -> AgentInfoVO.builder()
                        .name(agent.name())
                        .displayName(agent.displayName())
                        .description(agent.description())
                        .tools(agent.toolNames())
                        .build())
                .collect(Collectors.toList());
        return ResultUtils.success(list);
    }
}
