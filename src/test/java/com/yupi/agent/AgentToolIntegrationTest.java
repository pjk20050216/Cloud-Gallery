package com.yupi.agent;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AgentTool;
import com.yupi.agent.tool.ConfirmRequiredException;
import com.yupi.agent.tool.ToolGuard;
import com.yupi.agent.tool.ToolRegistry;
import com.yupi.agent.tool.ToolResult;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.model.entity.User;
import com.yupi.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具层单测：不依赖 LLM，直接验证注册、权限、必填校验、高危确认、真实执行。
 * <p>
 * 运行：mvn -o test -Dtest=AgentToolIntegrationTest
 */
@SpringBootTest
class AgentToolIntegrationTest {

    @Resource
    private ToolRegistry toolRegistry;

    @Resource
    private ToolGuard toolGuard;

    @Resource
    private UserService userService;

    private AgentContext adminContext() {
        return AgentContext.builder()
                .userId(900000000000000001L)
                .userAccount("agenttest")
                .userName("AgentTest")
                .admin(true)
                .permissions(java.util.Collections.singletonList("*"))
                .accessibleSpaceIds(new java.util.ArrayList<>())
                .traceId("test-trace")
                .build();
    }

    @Test
    void toolsShouldBeRegistered() {
        System.out.println(">>> 已注册工具：" + toolRegistry.all().size());
        toolRegistry.all().forEach(tool ->
                System.out.printf("    %-32s %s%n", tool.name(), tool.risk()));
        assertTrue(toolRegistry.all().size() >= 8);
        assertNotNull(toolRegistry.get("picture.delete"));
        assertEquals(ToolRiskLevel.DANGEROUS, toolRegistry.get("picture.delete").risk());
        assertEquals(ToolRiskLevel.READ_ONLY, toolRegistry.get("picture.search").risk());
    }

    @Test
    void dangerousToolShouldRequireConfirm() {
        AgentTool deleteTool = toolRegistry.get("picture.delete");
        Map<String, Object> args = new HashMap<>();
        args.put("pictureId", 1L);

        ConfirmRequiredException ex = assertThrows(ConfirmRequiredException.class,
                () -> toolGuard.check(adminContext(), deleteTool, args, false));
        System.out.println(">>> 高危确认文案：" + ex.getPreview());
        assertTrue(ex.getPreview().contains("删除") || ex.getPreview().contains("不可恢复"));

        // 已确认则放行
        assertDoesNotThrow(() -> toolGuard.check(adminContext(), deleteTool, args, true));
    }

    @Test
    void requiredArgsShouldBeValidated() {
        AgentTool uploadTool = toolRegistry.get("picture.uploadByUrl");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> toolGuard.check(adminContext(), uploadTool, new HashMap<>(), false));
        System.out.println(">>> 必填校验：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("url"));
    }

    @Test
    void permissionShouldBeChecked() {
        // 无任何权限的普通用户
        AgentContext noPerm = AgentContext.builder()
                .userId(900000000000000001L)
                .userAccount("agenttest")
                .admin(false)
                .permissions(java.util.Collections.emptyList())
                .accessibleSpaceIds(new java.util.ArrayList<>())
                .traceId("test-trace")
                .build();
        AgentTool editTool = toolRegistry.get("picture.edit");
        Map<String, Object> args = new HashMap<>();
        args.put("pictureId", 1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> toolGuard.check(noPerm, editTool, args, false));
        System.out.println(">>> 权限拦截：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("权限"));
    }

    @Test
    void searchToolShouldReturnRealData() {
        AgentTool searchTool = toolRegistry.get("picture.search");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("pageSize", 5);
        ToolResult result = searchTool.execute(adminContext(), args);
        System.out.println(">>> picture.search 结果 success=" + result.isSuccess()
                + " summary=" + result.getSummary());
        System.out.println(">>> data=" + result.toLlmString(600));
        assertTrue(result.isSuccess());
    }

    @Test
    void spaceToolShouldReturnRealData() {
        AgentTool spaceTool = toolRegistry.get("space.listMine");
        ToolResult result = spaceTool.execute(adminContext(), new HashMap<>());
        System.out.println(">>> space.listMine success=" + result.isSuccess()
                + " summary=" + result.getSummary());
        System.out.println(">>> data=" + result.toLlmString(600));
        assertTrue(result.isSuccess());
    }

    @Test
    void helpToolShouldWork() {
        ToolResult result = toolRegistry.get("help.listCapabilities").execute(adminContext(), new HashMap<>());
        System.out.println(">>> help: " + result.toLlmString(800));
        assertTrue(result.isSuccess());
    }

    @Test
    void userShouldBeResolvable() {
        User user = userService.getById(900000000000000001L);
        assertNotNull(user);
        System.out.println(">>> 测试用户：" + user.getUserAccount() + " role=" + user.getUserRole());
    }
}
