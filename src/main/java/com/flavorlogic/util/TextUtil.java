package com.flavorlogic.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文本处理工具：HTML 转义、内容指纹、LIKE 关键字转义等。
 */
public final class TextUtil {

    private TextUtil() {
    }

    /**
     * 是否为空或仅含空白。
     *
     * @param value 文本
     * @return true 表示为空
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 空值替换。
     *
     * @param value        原值
     * @param defaultValue 默认值
     * @return 原值或默认值
     */
    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    /**
     * HTML 转义，用于文章正文等富文本的安全展示，降低 XSS 风险。
     *
     * @param value 原始文本
     * @return 转义后的文本
     */
    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 轻量 HTML 白名单过滤：移除脚本、样式、内联事件与 javascript: 协议，
     * 用于文章正文的安全展示，降低 XSS 风险。
     *
     * @param html 原始 HTML
     * @return 过滤后的 HTML
     */
    public static String sanitizeHtml(String html) {
        if (html == null) {
            return "";
        }
        String safe = html.replaceAll("(?is)<(script|style|iframe|object|embed|link|meta)[^>]*>.*?</\\1>", "");
        safe = safe.replaceAll("(?is)<(script|style|iframe|object|embed|link|meta)[^>]*/?>", "");
        safe = safe.replaceAll("(?i)\\son[a-z]+\\s*=\\s*\"[^\"]*\"", "");
        safe = safe.replaceAll("(?i)\\son[a-z]+\\s*=\\s*'[^']*'", "");
        safe = safe.replaceAll("(?i)\\son[a-z]+\\s*=\\s*[^\\s>]+", "");
        safe = safe.replaceAll("(?i)javascript\\s*:", "");
        safe = safe.replaceAll("(?i)data\\s*:\\s*text/html", "");
        return safe;
    }

    /**
     * 去除 HTML 标签，用于生成正文摘要与内容指纹。
     *
     * @param html 原始 HTML
     * @return 纯文本
     */
    public static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        text = text.replaceAll("(?s)<[^>]+>", " ");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 计算 SHA-256 十六进制指纹，用于文章去重。
     *
     * @param text 文本
     * @return 64 位小写十六进制字符串
     */
    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 生成 LIKE 模糊匹配串，并转义 % _ \ 三个特殊字符。
     *
     * @param keyword 用户输入关键字
     * @return 可直接绑定到 {@code LIKE ? ESCAPE '\\'} 的参数
     */
    public static String likePattern(String keyword) {
        if (keyword == null) {
            return "%";
        }
        String escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * 截断文本。
     *
     * @param value     原文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 由正文生成摘要。
     *
     * @param html      正文 HTML
     * @param maxLength 摘要最大长度
     * @return 摘要文本
     */
    public static String summarize(String html, int maxLength) {
        String text = stripHtml(html);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…";
    }

    /**
     * 安全解析长整数。
     *
     * @param value        原始值
     * @param defaultValue 解析失败时的默认值
     * @return 解析结果
     */
    public static long parseLong(String value, long defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全解析整数。
     *
     * @param value        原始值
     * @param defaultValue 解析失败时的默认值
     * @return 解析结果
     */
    public static int parseInt(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
