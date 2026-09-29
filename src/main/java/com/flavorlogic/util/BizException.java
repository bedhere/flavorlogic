package com.flavorlogic.util;

/**
 * 业务异常：由 Service 层抛出，Servlet 层统一转换为失败响应。
 *
 * <p>与系统异常区分：业务异常携带可直接展示给用户的提示信息，
 * 系统异常在服务端记录日志后统一返回“服务器内部错误”。</p>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 参数错误 */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未登录 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 无权限 */
    public static final int CODE_FORBIDDEN = 403;
    /** 资源不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 状态冲突（重复数据、状态不允许等） */
    public static final int CODE_CONFLICT = 409;

    private final int code;

    public BizException(String message) {
        this(CODE_BAD_REQUEST, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 参数校验失败。
     *
     * @param message 提示信息
     * @return 业务异常
     */
    public static BizException badRequest(String message) {
        return new BizException(CODE_BAD_REQUEST, message);
    }

    /**
     * 未登录。
     *
     * @param message 提示信息
     * @return 业务异常
     */
    public static BizException unauthorized(String message) {
        return new BizException(CODE_UNAUTHORIZED, message);
    }

    /**
     * 无权限。
     *
     * @param message 提示信息
     * @return 业务异常
     */
    public static BizException forbidden(String message) {
        return new BizException(CODE_FORBIDDEN, message);
    }

    /**
     * 资源不存在。
     *
     * @param message 提示信息
     * @return 业务异常
     */
    public static BizException notFound(String message) {
        return new BizException(CODE_NOT_FOUND, message);
    }

    /**
     * 数据冲突。
     *
     * @param message 提示信息
     * @return 业务异常
     */
    public static BizException conflict(String message) {
        return new BizException(CODE_CONFLICT, message);
    }
}
