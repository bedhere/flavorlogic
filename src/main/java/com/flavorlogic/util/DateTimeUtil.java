package com.flavorlogic.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具：统一数据库时间与接口输出的格式。
 */
public final class DateTimeUtil {

    /** 接口与页面统一时间格式 */
    public static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(PATTERN);

    private DateTimeUtil() {
    }

    /**
     * 数据库时间转字符串。
     *
     * @param timestamp 数据库时间，可为 null
     * @return 格式化时间，null 输入返回 null
     */
    public static String format(Timestamp timestamp) {
        return timestamp == null ? null : FORMATTER.format(timestamp.toLocalDateTime());
    }

    /**
     * 本地时间转字符串。
     *
     * @param dateTime 本地时间，可为 null
     * @return 格式化时间，null 输入返回 null
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : FORMATTER.format(dateTime);
    }

    /**
     * 当前时间字符串。
     *
     * @return 当前时间
     */
    public static String now() {
        return FORMATTER.format(LocalDateTime.now());
    }

    /**
     * 字符串转数据库时间。
     *
     * @param text 时间文本（yyyy-MM-dd HH:mm:ss）
     * @return 数据库时间，解析失败返回 null
     */
    public static Timestamp toTimestamp(String text) {
        if (TextUtil.isBlank(text)) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(text.trim(), FORMATTER));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
