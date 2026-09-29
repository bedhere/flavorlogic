package com.flavorlogic.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

/**
 * JSON 工具：统一封装 Gson，负责请求体解析与响应输出。
 *
 * <p>前端统一以 {@code application/json} 提交；同时兼容
 * {@code application/x-www-form-urlencoded} 与查询参数，便于接口测试。</p>
 */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private JsonUtil() {
    }

    /**
     * 获取全局 Gson 实例。
     *
     * @return Gson 实例
     */
    public static Gson gson() {
        return GSON;
    }

    /**
     * 对象转 JSON 字符串。
     *
     * @param value 任意对象
     * @return JSON 文本
     */
    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    /**
     * 解析 JSON 文本。
     *
     * @param json JSON 文本
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 解析结果
     */
    public static <T> T parse(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    /**
     * 读取请求体并绑定为对象。
     *
     * @param request HTTP 请求
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 绑定结果，请求体为空时返回 null
     */
    public static <T> T readBody(HttpServletRequest request, Class<T> type) {
        JsonObject json = readBodyAsObject(request);
        if (json == null) {
            return null;
        }
        try {
            return GSON.fromJson(json, type);
        } catch (RuntimeException e) {
            throw BizException.badRequest("请求参数格式不正确");
        }
    }

    /**
     * 读取请求体为 JsonObject，兼容表单提交。
     *
     * @param request HTTP 请求
     * @return JsonObject，请求体为空时返回 null
     */
    public static JsonObject readBodyAsObject(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            } catch (IOException e) {
                throw BizException.badRequest("请求体读取失败");
            }
            String raw = sb.toString().trim();
            if (raw.isEmpty()) {
                return null;
            }
            try {
                JsonElement element = JsonParser.parseString(raw);
                if (element.isJsonObject()) {
                    return element.getAsJsonObject();
                }
                throw BizException.badRequest("请求体必须是 JSON 对象");
            } catch (RuntimeException e) {
                throw BizException.badRequest("请求体不是合法的 JSON");
            }
        }
        JsonObject fromParams = fromParameters(request);
        return fromParams.size() == 0 ? null : fromParams;
    }

    /**
     * 读取查询参数并绑定为对象（GET 请求的筛选条件）。
     *
     * @param request HTTP 请求
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 绑定结果
     */
    public static <T> T readQuery(HttpServletRequest request, Class<T> type) {
        JsonObject json = fromParameters(request);
        try {
            return GSON.fromJson(json, type);
        } catch (RuntimeException e) {
            throw BizException.badRequest("查询参数格式不正确");
        }
    }

    private static JsonObject fromParameters(HttpServletRequest request) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                continue;
            }
            if (values.length == 1) {
                json.addProperty(entry.getKey(), values[0]);
            } else {
                com.google.gson.JsonArray array = new com.google.gson.JsonArray();
                for (String value : values) {
                    array.add(value);
                }
                json.add(entry.getKey(), array);
            }
        }
        return json;
    }

    /**
     * 输出成功响应。
     *
     * @param response HTTP 响应
     * @param data     业务数据
     */
    public static void writeOk(HttpServletResponse response, Object data) {
        write(response, HttpServletResponse.SC_OK, ApiResponse.ok(data));
    }

    /**
     * 输出成功响应（无数据）。
     *
     * @param response HTTP 响应
     */
    public static void writeOk(HttpServletResponse response) {
        write(response, HttpServletResponse.SC_OK, ApiResponse.ok());
    }

    /**
     * 输出失败响应。
     *
     * @param response  HTTP 响应
     * @param httpStatus HTTP 状态码
     * @param code      业务码
     * @param message   提示信息
     */
    public static void writeError(HttpServletResponse response, int httpStatus, int code, String message) {
        write(response, httpStatus, ApiResponse.fail(code, message));
    }

    /**
     * 输出统一结构。
     *
     * @param response   HTTP 响应
     * @param httpStatus HTTP 状态码
     * @param body       响应体
     */
    public static void write(HttpServletResponse response, int httpStatus, ApiResponse<?> body) {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(httpStatus);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try {
            response.getWriter().write(toJson(body));
            response.getWriter().flush();
        } catch (IOException e) {
            throw new IllegalStateException("响应输出失败", e);
        }
    }
}
