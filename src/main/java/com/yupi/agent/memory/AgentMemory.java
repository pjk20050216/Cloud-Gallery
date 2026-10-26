package com.yupi.agent.memory;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 结构化记忆。
 * <p>
 * 为什么要单独存而不是只靠自然语言摘要：
 * 雪花 ID 是 19 位数字，自由文本摘要极易写错一位或直接省略，
 * 而"把刚才那张图删掉"这类指令**依赖精确 ID**。
 * 所以关键实体结构化保存、原样回灌，摘要只负责叙述性内容。
 */
@Data
@Slf4j
public class AgentMemory implements Serializable {

    /**
     * 涉及过的图片
     */
    private List<EntityRef> pictures = new ArrayList<>();

    /**
     * 涉及过的空间
     */
    private List<EntityRef> spaces = new ArrayList<>();

    /**
     * 提交过的异步任务号
     */
    private List<String> tasks = new ArrayList<>();

    /**
     * 其他稳定事实（用户偏好、身份、约束等）
     */
    private List<String> facts = new ArrayList<>();

    @Data
    public static class EntityRef implements Serializable {
        private String id;
        private String name;

        public EntityRef() {
        }

        public EntityRef(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final long serialVersionUID = 1L;

    // ---------------------------------------------------------------- 序列化

    public static AgentMemory fromJson(String json, ObjectMapper mapper) {
        if (StrUtil.isBlank(json)) {
            return new AgentMemory();
        }
        try {
            // 容错：老版本存的数据里可能有当前类不认识的字段，不要让整条记忆丢掉
            ObjectMapper lenient = mapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            AgentMemory memory = lenient.readValue(json, AgentMemory.class);
            return memory == null ? new AgentMemory() : memory;
        } catch (Exception e) {
            log.warn("解析结构化记忆失败，按空记忆处理：{}", e.getMessage());
            return new AgentMemory();
        }
    }

    public String toJson(ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            log.warn("序列化结构化记忆失败：{}", e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- 合并

    /**
     * 合并新抽取的记忆（去重，保留最近的）
     */
    public AgentMemory merge(AgentMemory other) {
        if (other == null) {
            return this;
        }
        this.pictures = mergeRefs(this.pictures, other.getPictures());
        this.spaces = mergeRefs(this.spaces, other.getSpaces());
        this.tasks = mergeStrings(this.tasks, other.getTasks(), 10);
        this.facts = mergeStrings(this.facts, other.getFacts(), 20);
        return this;
    }

    private List<EntityRef> mergeRefs(List<EntityRef> base, List<EntityRef> incoming) {
        List<EntityRef> result = new ArrayList<>(base == null ? new ArrayList<>() : base);
        Set<String> seen = new LinkedHashSet<>();
        for (EntityRef ref : result) {
            seen.add(ref.getId());
        }
        if (incoming != null) {
            for (EntityRef ref : incoming) {
                if (StrUtil.isBlank(ref.getId()) || seen.contains(ref.getId())) {
                    continue;
                }
                seen.add(ref.getId());
                result.add(ref);
            }
        }
        // 只保留最近的 10 个，避免无限膨胀
        if (result.size() > 10) {
            result = new ArrayList<>(result.subList(result.size() - 10, result.size()));
        }
        return result;
    }

    private List<String> mergeStrings(List<String> base, List<String> incoming, int max) {
        List<String> result = new ArrayList<>(base == null ? new ArrayList<>() : base);
        if (incoming != null) {
            for (String item : incoming) {
                if (StrUtil.isBlank(item) || result.contains(item)) {
                    continue;
                }
                result.add(item);
            }
        }
        if (result.size() > max) {
            result = new ArrayList<>(result.subList(result.size() - max, result.size()));
        }
        return result;
    }

    /**
     * 是否为空
     * <p>
     * 必须标 @JsonIgnore：否则 Jackson 会把它当成属性 "empty" 写进 JSON，
     * 反序列化时又因没有对应 setter 而失败（这个 bug 实际踩过）。
     */
    @JsonIgnore
    public boolean isEmpty() {
        return (pictures == null || pictures.isEmpty())
                && (spaces == null || spaces.isEmpty())
                && (tasks == null || tasks.isEmpty())
                && (facts == null || facts.isEmpty());
    }

    /**
     * 渲染成给模型看的一段文本
     */
    public String render() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【已知信息（来自更早的对话，可直接使用这些 id）】\n");
        appendRefs(sb, "图片", pictures);
        appendRefs(sb, "空间", spaces);
        if (tasks != null && !tasks.isEmpty()) {
            sb.append("- 异步任务号：").append(String.join("、", tasks)).append("\n");
        }
        if (facts != null && !facts.isEmpty()) {
            for (String fact : facts) {
                sb.append("- ").append(fact).append("\n");
            }
        }
        return sb.toString();
    }

    private void appendRefs(StringBuilder sb, String label, List<EntityRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        sb.append("- 涉及过的").append(label).append("：");
        List<String> parts = new ArrayList<>();
        for (EntityRef ref : refs) {
            parts.add(StrUtil.isBlank(ref.getName())
                    ? "id=" + ref.getId()
                    : ref.getName() + "（id=" + ref.getId() + "）");
        }
        sb.append(String.join("、", parts)).append("\n");
    }
}
