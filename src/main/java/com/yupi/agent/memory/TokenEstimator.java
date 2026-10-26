package com.yupi.agent.memory;

/**
 * 轻量 token 估算。
 * <p>
 * 项目里没有 tokenizer（引 tiktoken 之类会多几百 KB 依赖，且千问的分词器未必一致），
 * 所以用字符启发式估算，**宁可高估**——高估只会让压缩提前触发，不会撑爆上下文。
 * <p>
 * 经验值（千问系列）：
 * <ul>
 *     <li>中文：约 1 字 = 1.0~1.5 token，这里按 1.2</li>
 *     <li>英文/数字/符号：约 4 字符 = 1 token</li>
 * </ul>
 */
public final class TokenEstimator {

    /**
     * 中文字符的 token 系数（保守取 1.2）
     */
    private static final double CJK_FACTOR = 1.2;

    /**
     * 非中文字符的每 token 字符数
     */
    private static final int ASCII_CHARS_PER_TOKEN = 4;

    /**
     * 每条消息的固定开销（role、分隔符等）
     */
    private static final int MESSAGE_OVERHEAD = 4;

    private TokenEstimator() {
    }

    /**
     * 估算一段文本的 token 数
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else {
                other++;
            }
        }
        int tokens = (int) Math.ceil(cjk * CJK_FACTOR)
                + (int) Math.ceil(other / (double) ASCII_CHARS_PER_TOKEN);
        return Math.max(1, tokens);
    }

    /**
     * 估算一条消息（含 role 开销）
     */
    public static int estimateMessage(String role, String content) {
        return estimate(role) + estimate(content) + MESSAGE_OVERHEAD;
    }

    /**
     * 是否中日韩字符
     */
    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)      // 常用汉字
                || (c >= 0x3400 && c <= 0x4DBF)  // 扩展 A
                || (c >= 0x3000 && c <= 0x303F)  // 中文标点
                || (c >= 0xFF00 && c <= 0xFFEF); // 全角字符
    }

    /**
     * 按 token 预算截断文本（尽量在句子边界截断）
     */
    public static String truncateToTokens(String text, int maxTokens) {
        if (text == null || maxTokens <= 0) {
            return text;
        }
        if (estimate(text) <= maxTokens) {
            return text;
        }
        // 粗略反推字符数，再按标点回退
        int roughChars = Math.max(1, (int) (maxTokens / CJK_FACTOR));
        if (roughChars >= text.length()) {
            return text;
        }
        int cut = roughChars;
        // 往前找一个句子边界，避免截断在半个词里
        int floor = Math.max(0, roughChars - 40);
        for (int i = roughChars; i > floor; i--) {
            char c = text.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '\n'
                    || c == '.' || c == '!' || c == '?' || c == ';' || c == '；') {
                cut = i;
                break;
            }
        }
        return text.substring(0, cut) + "…（已截断）";
    }
}
