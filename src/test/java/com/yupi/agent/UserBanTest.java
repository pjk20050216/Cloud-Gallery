package com.yupi.agent;

import com.yupi.agent.core.AgentContext;
import com.yupi.agent.tool.AgentTool;
import com.yupi.agent.tool.ToolGuard;
import com.yupi.agent.tool.ToolNames;
import com.yupi.agent.tool.ToolRegistry;
import com.yupi.agent.tool.ToolRiskLevel;
import com.yupi.model.dto.ban.BanAppealReviewRequest;
import com.yupi.model.dto.ban.UserBanRequest;
import com.yupi.model.dto.ban.UserUnbanRequest;
import com.yupi.model.entity.User;
import com.yupi.model.entity.UserBanRecord;
import com.yupi.model.vo.UserBanRecordVO;
import com.yupi.service.UserBanService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 封禁体系 + 智能体联动测试。
 * <p>
 * 运行：mvn -o test -Dtest=UserBanTest
 */
@SpringBootTest
class UserBanTest {

    private static final long BANNED_USER = 900000000000000004L;
    private static final long OPERATOR = 900000000000000001L;

    @Resource
    private UserBanService userBanService;

    @Resource
    private ToolGuard toolGuard;

    @Resource
    private ToolRegistry toolRegistry;

    private AgentContext bannedContext() {
        return AgentContext.builder()
                .userId(BANNED_USER)
                .userAccount("banned")
                .admin(false)
                .banned(true)
                .banMessage("账号当前处于封禁状态。原因：测试原因。解封时间：永久。")
                .permissions(new ArrayList<>())
                .accessibleSpaceIds(new ArrayList<>())
                .traceId("test")
                .build();
    }

    private User operator() {
        User user = new User();
        user.setId(OPERATOR);
        user.setUserAccount("agenttest");
        user.setUserRole("admin");
        return user;
    }

    @Test
    void banLifecycleShouldWork() {
        // 先清掉可能残留的生效中记录
        for (UserBanRecord old : userBanService.listByUser(BANNED_USER)) {
            if (Integer.valueOf(0).equals(old.getStatus())) {
                UserUnbanRequest cleanup = new UserUnbanRequest();
                cleanup.setBanRecordId(old.getId());
                cleanup.setUnbanReason("测试清理");
                userBanService.unban(cleanup, operator());
            }
        }

        UserBanRequest request = new UserBanRequest();
        request.setUserId(BANNED_USER);
        request.setBanType(1);
        request.setReasonCode("VIOLENT_CONTENT");
        request.setReasonDetail("测试用：上传违规内容");
        request.setDurationDays(3);

        UserBanRecord record = userBanService.ban(request, operator());
        assertNotNull(record.getId());
        System.out.println(">>> 封禁成功 id=" + record.getId());

        assertTrue(userBanService.isBanned(BANNED_USER));
        assertNotNull(userBanService.getActiveBan(BANNED_USER));

        // 重复封禁应被拒
        assertThrows(Exception.class, () -> userBanService.ban(request, operator()));
        System.out.println(">>> 重复封禁被正确拒绝");

        // 解封
        UserUnbanRequest unban = new UserUnbanRequest();
        unban.setBanRecordId(record.getId());
        unban.setUnbanReason("测试解封");
        assertTrue(userBanService.unban(unban, operator()));
        assertFalse(userBanService.isBanned(BANNED_USER));
        System.out.println(">>> 解封成功");
    }

    @Test
    void appealFlowShouldWork() {
        UserBanRequest request = new UserBanRequest();
        request.setUserId(BANNED_USER);
        request.setReasonDetail("测试用：申诉流程");
        request.setDurationDays(1);
        UserBanRecord record = userBanService.ban(request, operator());

        assertTrue(userBanService.submitAppeal(BANNED_USER, record.getId(), "我认为处罚有误"));
        System.out.println(">>> 申诉提交成功");

        // 重复申诉被拒
        assertThrows(Exception.class,
                () -> userBanService.submitAppeal(BANNED_USER, record.getId(), "再来一次"));
        System.out.println(">>> 重复申诉被正确拒绝");

        // 管理员驳回
        BanAppealReviewRequest review = new BanAppealReviewRequest();
        review.setBanRecordId(record.getId());
        review.setApprove(false);
        review.setReply("证据确凿，维持处罚");
        assertTrue(userBanService.reviewAppeal(review, operator()));

        UserBanRecordVO vo = userBanService.toVO(userBanService.getById(record.getId()));
        assertEquals("申诉驳回", vo.getAppealStatusText());
        System.out.println(">>> 申诉驳回处理完成");

        // 清理
        UserUnbanRequest unban = new UserUnbanRequest();
        unban.setBanRecordId(record.getId());
        userBanService.unban(unban, operator());
    }

    @Test
    void otherUserCannotAppeal() {
        UserBanRequest request = new UserBanRequest();
        request.setUserId(BANNED_USER);
        request.setReasonDetail("测试用：越权申诉");
        request.setDurationDays(1);
        UserBanRecord record = userBanService.ban(request, operator());

        assertThrows(Exception.class,
                () -> userBanService.submitAppeal(BANNED_USER + 999, record.getId(), "我是别人"));
        System.out.println(">>> 越权申诉被正确拒绝");

        UserUnbanRequest unban = new UserUnbanRequest();
        unban.setBanRecordId(record.getId());
        userBanService.unban(unban, operator());
    }

    @Test
    void bannedUserCanOnlyUseReadOnlyTools() {
        AgentContext ctx = bannedContext();

        // 只读工具放行
        AgentTool search = toolRegistry.get(ToolNames.PICTURE_SEARCH);
        assertDoesNotThrow(() -> toolGuard.check(ctx, search, new HashMap<>(), false));
        System.out.println(">>> 封禁用户可用只读工具 picture.search");

        // 写工具被拦
        AgentTool upload = toolRegistry.get(ToolNames.PICTURE_UPLOAD_BY_URL);
        Map<String, Object> args = new HashMap<>();
        args.put("url", "https://example.com/a.png");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> toolGuard.check(ctx, upload, args, false));
        System.out.println(">>> 封禁用户被拦截写工具：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("封禁"));

        // 申诉工具是特例，封禁期间仍可用
        AgentTool appeal = toolRegistry.get(ToolNames.ACCOUNT_SUBMIT_APPEAL);
        Map<String, Object> appealArgs = new HashMap<>();
        appealArgs.put("banRecordId", 1L);
        appealArgs.put("content", "我要申诉");
        assertDoesNotThrow(() -> toolGuard.check(ctx, appeal, appealArgs, false));
        System.out.println(">>> 封禁用户仍可提交申诉（allowedWhenBanned）");

        // 查看处罚记录也放行
        AgentTool records = toolRegistry.get(ToolNames.ACCOUNT_MY_BAN_RECORDS);
        assertDoesNotThrow(() -> toolGuard.check(ctx, records, new HashMap<>(), false));
        System.out.println(">>> 封禁用户可查看自己的处罚记录");
    }

    @Test
    void accountAgentShouldHaveBanTools() {
        List<String> tools = new ArrayList<>(toolRegistry.all()).size() > 0
                ? java.util.Arrays.asList(ToolNames.ACCOUNT_MY_BAN_RECORDS,
                ToolNames.ACCOUNT_SUBMIT_APPEAL, ToolNames.ACCOUNT_MY_STATUS)
                : new ArrayList<>();
        for (String toolName : tools) {
            assertNotNull(toolRegistry.get(toolName), "缺少工具：" + toolName);
        }
        System.out.println(">>> 封禁相关工具齐备：" + tools);
        assertEquals(ToolRiskLevel.READ_ONLY, toolRegistry.get(ToolNames.ACCOUNT_MY_BAN_RECORDS).risk());
        assertTrue(toolRegistry.get(ToolNames.ACCOUNT_SUBMIT_APPEAL).allowedWhenBanned());
    }
}
