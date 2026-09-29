package com.flavorlogic.util;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 操作日志：记录管理员的关键数据修改行为（操作者、动作、对象、结果、时间）。
 *
 * <p>数据库脚本未包含日志表，第一阶段采用文件日志落盘，
 * 落盘位置：系统属性 {@code flavorlogic.log.file} &gt; 环境变量 {@code FLAVORLOGIC_LOG_FILE}
 * &gt; {@code ${catalina.base}/logs/flavorlogic-operations.log} &gt; 临时目录。</p>
 */
public final class OperationLog {

    private static final Logger LOG = Logger.getLogger(OperationLog.class.getName());
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();
    private static final long MAX_READ_BYTES = 2L * 1024 * 1024;

    private OperationLog() {
    }

    /**
     * 记录一条操作日志。
     *
     * @param request HTTP 请求（用于取操作者与来源 IP，可为 null）
     * @param action  操作动作，例如“新增食材”
     * @param target  操作对象，例如“食材#12 花椒粉”
     * @param result  操作结果，例如“成功”“失败：名称重复”
     */
    public static void record(HttpServletRequest request, String action, String target, String result) {
        String operator = "anonymous";
        String ip = "-";
        if (request != null) {
            // 与 SessionUtil 中的 Session 属性名保持一致，避免记录成 anonymous
            String username = SessionUtil.currentUsername(request);
            if (!TextUtil.isBlank(username)) {
                operator = username;
            }
            ip = clientIp(request);
        }
        record(operator, action, target, result, ip);
    }

    /**
     * 记录一条操作日志。
     *
     * @param operator 操作者
     * @param action   操作动作
     * @param target   操作对象
     * @param result   操作结果
     * @param ip       来源 IP
     */
    public static void record(String operator, String action, String target, String result, String ip) {
        String line = String.format("%s | user=%s | ip=%s | %s | %s | %s",
                LocalDateTime.now().format(FORMATTER),
                TextUtil.defaultIfBlank(operator, "anonymous"),
                TextUtil.defaultIfBlank(ip, "-"),
                TextUtil.defaultIfBlank(action, "-"),
                TextUtil.defaultIfBlank(target, "-"),
                TextUtil.defaultIfBlank(result, "-"));
        synchronized (LOCK) {
            try {
                Path file = logFile();
                Files.createDirectories(file.getParent());
                Files.write(file, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "写入操作日志失败：" + line, e);
            }
        }
    }

    /**
     * 读取最近的日志（最新在前），供后台“操作日志”页面展示。
     *
     * @param maxLines 最多返回行数
     * @return 日志行列表
     */
    public static List<String> tail(int maxLines) {
        synchronized (LOCK) {
            try {
                Path file = logFile();
                if (!Files.exists(file) || Files.size(file) > MAX_READ_BYTES) {
                    if (!Files.exists(file)) {
                        return Collections.emptyList();
                    }
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                List<String> result = new ArrayList<>();
                for (int i = lines.size() - 1; i >= 0 && result.size() < maxLines; i--) {
                    String line = lines.get(i);
                    if (!TextUtil.isBlank(line)) {
                        result.add(line);
                    }
                }
                return result;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "读取操作日志失败", e);
                return Collections.emptyList();
            }
        }
    }

    /**
     * 日志文件路径。
     *
     * @return 日志文件路径
     */
    public static Path logFile() {
        String configured = System.getProperty("flavorlogic.log.file");
        if (TextUtil.isBlank(configured)) {
            configured = System.getenv("FLAVORLOGIC_LOG_FILE");
        }
        if (!TextUtil.isBlank(configured)) {
            return Paths.get(configured);
        }
        String catalinaBase = System.getProperty("catalina.base");
        if (!TextUtil.isBlank(catalinaBase)) {
            return Paths.get(catalinaBase, "logs", "flavorlogic-operations.log");
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "flavorlogic-operations.log");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!TextUtil.isBlank(forwarded)) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
