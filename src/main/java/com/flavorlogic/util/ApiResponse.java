package com.flavorlogic.util;

import java.io.Serializable;

/**
 * 统一接口返回结构：{@code {code, message, data, timestamp}}。
 *
 * <p>约定：{@code code = 0} 表示成功，非 0 表示业务失败；HTTP 状态码由 Servlet 决定，
 * 业务失败默认仍返回 200，鉴权失败返回 401/403。</p>
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0 成功 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;
    /** 服务端时间戳 */
    private long timestamp;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 统一响应体
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    /**
     * 成功响应（无数据）。
     *
     * @return 统一响应体
     */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "success", null);
    }

    /**
     * 成功响应（自定义提示）。
     *
     * @param message 提示信息
     * @param data    业务数据
     * @param <T>     数据类型
     * @return 统一响应体
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(0, message, data);
    }

    /**
     * 失败响应。
     *
     * @param code    业务码
     * @param message 提示信息
     * @param <T>     数据类型
     * @return 统一响应体
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
