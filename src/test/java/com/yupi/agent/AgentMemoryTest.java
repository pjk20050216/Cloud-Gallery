package com.yupi.agent;

import com.yupi.agent.llm.model.ChatMessage;
import com.yupi.agent.memory.AgentMemory;
import com.yupi.agent.memory.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上下文压缩的纯逻辑测试（不依赖 Spring / LLM）。
 * <p>
 * 运行：mvn -o test -Dtest=AgentMemoryTest
 */
class AgentMemoryTest {

    // ================================================================ TokenEstimator

    @Test
    void tokenEstimatorShouldBeReasonable() {
        // 中文按 1.2/字：12 个汉字 → ceil(14.4) = 15
        int cjk = TokenEstimator.estimate("你好世界你好世界你好世界");
        System.out.println(">>> 12 个汉字 ≈ " + cjk + " tokens");
        assertEquals(15, cjk);
        // 空串
        assertEquals(0, TokenEstimator.estimate(""));
        assertEquals(0, TokenEstimator.estimate(null));
        // 英文 4 字符/token
        int en = TokenEstimator.estimate("hello world this is a test");
        System.out.println(">>> 英文 26 字符 ≈ " + en + " tokens");
        assertTrue(en >= 5 && en <= 10);

        // 中英混合
        String mixed = "请帮我找一下 cat 的图片";
        int mixedTokens = TokenEstimator.estimate(mixed);
        System.out.println(">>> 中英混合 \"" + mixed + "\" ≈ " + mixedTokens + " tokens");
        assertTrue(mixedTokens > 0);
    }

    @Test
    void truncateShouldRespectBudget() {
        String longText = "这是一段很长的中文文本。".repeat(200);
        int before = TokenEstimator.estimate(longText);
        String truncated = TokenEstimator.truncateToTokens(longText, 100);
        int after = TokenEstimator.estimate(truncated);
        System.out.println(">>> 截断：" + before + " -> " + after + " tokens");
        assertTrue(after <= 130, "截断后应接近预算");
        assertTrue(truncated.endsWith("（已截断）"));

        // 未超预算不截断
        String shortText = "短文本";
        assertEquals(shortText, TokenEstimator.truncateToTokens(shortText, 100));
    }

    // ================================================================ AgentMemory

    @Test
    void memoryShouldMergeAndDedupe() {
        AgentMemory base = new AgentMemory();
        base.getPictures().add(new AgentMemory.EntityRef("111", "小猫"));
        base.getTasks().add("task-1");
        base.getFacts().add("用户是管理员");

        AgentMemory incoming = new AgentMemory();
        // 重复的 id 应被去重
        incoming.getPictures().add(new AgentMemory.EntityRef("111", "小猫"));
        incoming.getPictures().add(new AgentMemory.EntityRef("222", "小狗"));
        incoming.getTasks().add("task-1");
        incoming.getTasks().add("task-2");
        incoming.getFacts().add("用户偏好简短回答");

        base.merge(incoming);

        assertEquals(2, base.getPictures().size(), "图片应去重");
        assertEquals(2, base.getTasks().size(), "任务应去重");
        assertEquals(2, base.getFacts().size(), "事实应去重");

        System.out.println(">>> 合并后记忆：");
        System.out.println(base.render());
        assertTrue(base.render().contains("222"));
    }

    @Test
    void memoryShouldCapSize() {
        AgentMemory memory = new AgentMemory();
        for (int i = 0; i < 30; i++) {
            memory.getPictures().add(new AgentMemory.EntityRef("id-" + i, "图" + i));
        }
        AgentMemory empty = new AgentMemory();
        memory.merge(empty);
        // merge 会触发裁剪
        assertEquals(10, memory.getPictures().size(), "最多保留 10 个实体");
        System.out.println(">>> 裁剪后保留：" + memory.getPictures().size() + " 个");
    }

    @Test
    void memoryShouldRenderIdsExactly() {
        AgentMemory memory = new AgentMemory();
        // 19 位雪花 id，必须原样渲染
        memory.getPictures().add(new AgentMemory.EntityRef("2113537314025197569", "小猫"));
        String rendered = memory.render();
        System.out.println(">>> 渲染结果：\n" + rendered);
        assertTrue(rendered.contains("2113537314025197569"), "雪花 id 必须一字不差");
    }

    @Test
    void emptyMemoryShouldRenderNothing() {
        assertEquals("", new AgentMemory().render());
        assertTrue(new AgentMemory().isEmpty());
    }

    @Test
    void memoryJsonRoundTrip() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        AgentMemory memory = new AgentMemory();
        memory.getPictures().add(new AgentMemory.EntityRef("1234567890123456789", "测试图"));
        memory.getSpaces().add(new AgentMemory.EntityRef("9876543210987654321", "测试空间"));
        memory.getTasks().add("task-abc");
        memory.getFacts().add("用户是管理员");

        String json = memory.toJson(mapper);
        System.out.println(">>> 序列化：" + json);

        AgentMemory parsed = AgentMemory.fromJson(json, mapper);
        assertEquals(1, parsed.getPictures().size());
        assertEquals("1234567890123456789", parsed.getPictures().get(0).getId());
        assertEquals("测试图", parsed.getPictures().get(0).getName());
        assertEquals(1, parsed.getSpaces().size());
        assertEquals(1, parsed.getTasks().size());
        assertEquals(1, parsed.getFacts().size());

        // 脏数据不应抛异常
        assertTrue(AgentMemory.fromJson("not a json", mapper).isEmpty());
        assertTrue(AgentMemory.fromJson(null, mapper).isEmpty());
        assertTrue(AgentMemory.fromJson("", mapper).isEmpty());
    }

    // ================================================================ 分层上下文

    @Test
    void layeredContextShouldEstimateCorrectly() {
        // 构造一段足够长的对话（每条约 60 token，40 条约 2400 token）
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            history.add(ChatMessage.user("这是第 " + i + " 轮用户消息，内容比较长，"
                    + "用于测试 token 估算逻辑是否正确，需要凑够一定长度才能触发压缩。"));
            history.add(ChatMessage.assistant("这是第 " + i + " 轮助手回复，同样包含一些描述性内容，"
                    + "让整体上下文长度超过保留预算。"));
        }

        int total = 0;
        for (ChatMessage m : history) {
            total += TokenEstimator.estimateMessage(m.getRole(), m.getContent());
        }
        System.out.println(">>> 40 条消息共约 " + total + " tokens");

        // 保留最近 500 tokens（远小于总量，确保有消息被压缩）
        int keepBudget = 500;
        int acc = 0;
        int from = history.size();
        for (int i = history.size() - 1; i >= 0; i--) {
            acc += TokenEstimator.estimateMessage(history.get(i).getRole(), history.get(i).getContent());
            if (acc > keepBudget) break;
            from = i;
        }
        int kept = history.size() - from;
        System.out.println(">>> 保留最近 " + kept + " 条（约 " + acc + " tokens），其余 " + from + " 条会被压缩");
        assertTrue(total > keepBudget, "总量应超过预算");
        assertTrue(kept < history.size(), "应有消息被压缩");
        assertTrue(kept > 0, "应保留部分消息");
    }
}
