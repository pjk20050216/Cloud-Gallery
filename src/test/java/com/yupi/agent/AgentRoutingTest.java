package com.yupi.agent;

import com.yupi.agent.agent.SubAgent;
import com.yupi.agent.agent.SubAgentRegistry;
import com.yupi.agent.core.AgentContext;
import com.yupi.agent.core.AgentRouter;
import com.yupi.agent.tool.ToolNames;
import com.yupi.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多智能体路由与工具隔离测试（不依赖 LLM：规则命中的用例不会走分类器）。
 * <p>
 * 运行：mvn -o test -Dtest=AgentRoutingTest
 */
@SpringBootTest
class AgentRoutingTest {

    @Resource
    private AgentRouter agentRouter;

    @Resource
    private SubAgentRegistry subAgentRegistry;

    @Resource
    private ToolRegistry toolRegistry;

    private AgentContext context() {
        return AgentContext.builder()
                .userId(900000000000000001L)
                .userAccount("agenttest")
                .admin(true)
                .permissions(java.util.Collections.singletonList("*"))
                .accessibleSpaceIds(new ArrayList<>())
                .traceId("test")
                .build();
    }

    @Test
    void allAgentsShouldBeRegistered() {
        System.out.println(">>> 已注册智能体：");
        subAgentRegistry.all().forEach(agent ->
                System.out.printf("    %-10s %-8s tools=%s%n",
                        agent.name(), agent.displayName(), agent.toolNames()));
        assertEquals(4, subAgentRegistry.all().size());
        assertNotNull(subAgentRegistry.get("picture"));
        assertNotNull(subAgentRegistry.get("space"));
        assertNotNull(subAgentRegistry.get("account"));
        assertEquals("general", subAgentRegistry.getDefault().name());
    }

    @Test
    void ruleRoutingShouldWork() {
        assertRoute("帮我把这张图传到图库", "picture");
        assertRoute("找一下猫的图片", "picture");
        assertRoute("把「小猫」删掉", "picture");
        assertRoute("我账号为什么被封了", "account");
        assertRoute("我的额度还剩多少", "account");
        assertRoute("我有哪些空间", "space");
        assertRoute("空间容量用了多少", "space");
    }

    private void assertRoute(String input, String expected) {
        SubAgent agent = agentRouter.route(context(), input);
        System.out.printf("    %-24s -> %s%n", input, agent.name());
        assertEquals(expected, agent.name(), "输入：" + input);
    }

    @Test
    void toolWhitelistShouldBeIsolated() {
        SubAgent picture = subAgentRegistry.get("picture");
        SubAgent space = subAgentRegistry.get("space");
        SubAgent account = subAgentRegistry.get("account");

        // 图片管家不能删空间、不能查账号
        assertTrue(picture.toolNames().contains(ToolNames.PICTURE_DELETE));
        assertFalse(picture.toolNames().contains(ToolNames.ACCOUNT_MY_STATUS));

        // 空间管家没有写操作
        assertFalse(space.toolNames().contains(ToolNames.PICTURE_UPLOAD_BY_URL));
        assertFalse(space.toolNames().contains(ToolNames.PICTURE_DELETE));

        // 账号助手没有图片写操作
        assertFalse(account.toolNames().contains(ToolNames.PICTURE_DELETE));
        assertTrue(account.toolNames().contains(ToolNames.ACCOUNT_MY_STATUS));

        System.out.println(">>> 工具白名单隔离校验通过");
    }

    @Test
    void everyAgentToolShouldExistInRegistry() {
        for (SubAgent agent : subAgentRegistry.all()) {
            for (String toolName : agent.toolNames()) {
                assertNotNull(toolRegistry.get(toolName),
                        "智能体 " + agent.name() + " 引用了不存在的工具：" + toolName);
            }
        }
        System.out.println(">>> 所有智能体引用的工具都已注册，共 " + toolRegistry.all().size() + " 个");
    }

    @Test
    void accountStatusToolShouldBeHonest() {
        var tool = toolRegistry.get(ToolNames.ACCOUNT_MY_STATUS);
        var result = tool.execute(context(), new java.util.HashMap<>());
        System.out.println(">>> account.myStatus: " + result.toLlmString(600));
        assertTrue(result.isSuccess());
        // 不能编造封禁记录
        assertTrue(String.valueOf(result.getData()).contains("banFeatureAvailable=false")
                || String.valueOf(result.getData()).contains("banned=false"));
    }
}
