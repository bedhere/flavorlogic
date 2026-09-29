package com.flavorlogic.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 参数校验工具：所有方法校验失败时抛出 {@link BizException}（code = 400），
 * 提示信息可直接展示给用户。
 */
public final class ValidationUtil {

    /** 用户名：支持中文姓名、字母、数字与下划线，长度 2-20 */
    private static final Pattern USERNAME = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_]{2,20}$");
    private static final Pattern URL_LIKE = Pattern.compile("^(https?://|/)[^\\s]{1,490}$");

    private ValidationUtil() {
    }

    /**
     * 必填文本，自动去除首尾空白。
     *
     * @param value     原始值
     * @param field     字段中文名，用于拼装提示
     * @param maxLength 最大长度
     * @return 去空白后的文本
     */
    public static String requireText(String value, String field, int maxLength) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw BizException.badRequest(field + "不能为空");
        }
        if (trimmed.length() > maxLength) {
            throw BizException.badRequest(field + "长度不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }

    /**
     * 选填文本：空白值统一归一化为 null。
     *
     * @param value     原始值
     * @param field     字段中文名
     * @param maxLength 最大长度
     * @return 去空白后的文本或 null
     */
    public static String optionalText(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw BizException.badRequest(field + "长度不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }

    /**
     * 必填整数。
     *
     * @param value 原始值
     * @param field 字段中文名
     * @param min   最小值（含）
     * @param max   最大值（含）
     * @return 解析后的整数
     */
    public static int requireInt(String value, String field, int min, int max) {
        int parsed = parseInt(value, field);
        if (parsed < min || parsed > max) {
            throw BizException.badRequest(field + "必须在 " + min + " 到 " + max + " 之间");
        }
        return parsed;
    }

    /**
     * 解析整数。
     *
     * @param value 原始值
     * @param field 字段中文名
     * @return 解析后的整数
     */
    public static int parseInt(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw BizException.badRequest(field + "不能为空");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest(field + "必须是整数");
        }
    }

    /**
     * 解析长整数。
     *
     * @param value 原始值
     * @param field 字段中文名
     * @return 解析后的长整数
     */
    public static long parseLong(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw BizException.badRequest(field + "不能为空");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest(field + "必须是数字编号");
        }
    }

    /**
     * 必填小数并校验区间。
     *
     * @param value 原始值
     * @param field 字段中文名
     * @param min   最小值（含）
     * @param max   最大值（含）
     * @return 解析后的小数
     */
    public static BigDecimal requireDecimal(String value, String field, BigDecimal min, BigDecimal max) {
        BigDecimal parsed = parseDecimal(value, field);
        if (min != null && parsed.compareTo(min) < 0) {
            throw BizException.badRequest(field + "不能小于 " + min.stripTrailingZeros().toPlainString());
        }
        if (max != null && parsed.compareTo(max) > 0) {
            throw BizException.badRequest(field + "不能大于 " + max.stripTrailingZeros().toPlainString());
        }
        return parsed;
    }

    /**
     * 解析小数。
     *
     * @param value 原始值
     * @param field 字段中文名
     * @return 解析后的小数
     */
    public static BigDecimal parseDecimal(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw BizException.badRequest(field + "不能为空");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest(field + "必须是数字");
        }
    }

    /**
     * 枚举取值校验。
     *
     * @param value   原始值
     * @param field   字段中文名
     * @param allowed 允许的取值
     * @return 校验后的原值
     */
    public static String requireOneOf(String value, String field, String... allowed) {
        if (value == null) {
            throw BizException.badRequest(field + "不能为空");
        }
        String trimmed = value.trim();
        for (String item : allowed) {
            if (item.equals(trimmed)) {
                return trimmed;
            }
        }
        throw BizException.badRequest(field + "取值不合法");
    }

    /**
     * 用户名规则校验：支持中文姓名、字母、数字与下划线，长度 2-20。
     *
     * @param username 用户名
     * @return 校验后的用户名
     */
    public static String requireUsername(String username) {
        String trimmed = requireText(username, "用户名", 20);
        if (!USERNAME.matcher(trimmed).matches()) {
            throw BizException.badRequest("用户名只能包含中文、字母、数字和下划线，长度 2-20 位");
        }
        return trimmed;
    }

    /**
     * 密码规则校验：长度 6-20。
     *
     * @param password 密码
     * @return 校验后的密码
     */
    public static String requirePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 20) {
            throw BizException.badRequest("密码长度必须在 6-20 位之间");
        }
        return password;
    }

    /**
     * 文章封面/原文地址等可选地址校验。
     *
     * @param value 原始值
     * @param field 字段中文名
     * @return 校验后的地址或 null
     */
    public static String optionalUrl(String value, String field) {
        String trimmed = optionalText(value, 500, field);
        if (trimmed == null) {
            return null;
        }
        if (!URL_LIKE.matcher(trimmed).matches()) {
            throw BizException.badRequest(field + "必须以 http://、https:// 或 / 开头");
        }
        return trimmed;
    }
}
