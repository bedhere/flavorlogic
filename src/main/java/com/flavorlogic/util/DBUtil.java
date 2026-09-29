package com.flavorlogic.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 数据库连接工具。
 *
 * <p>配置读取优先级：环境变量 &gt; JVM 系统属性 &gt; classpath:db.properties &gt; 内置默认值。
 * 使用 JDBC {@link DriverManager} 直接获取连接（不使用任何第三方连接池或框架），
 * 所有连接必须在 finally 中通过 {@link #close(AutoCloseable...)} 归还。</p>
 *
 * @author 食之有理 FlavorLogic
 */
public final class DBUtil {

    private static final Logger LOG = Logger.getLogger(DBUtil.class.getName());

    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3306/flavor_logic?useUnicode=true&characterEncoding=UTF-8"
                    + "&characterSetResults=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false"
                    + "&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";
    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";

    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        Properties fileConfig = loadProperties();
        URL = firstNonBlank(System.getenv("FLAVORLOGIC_DB_URL"),
                System.getProperty("flavorlogic.db.url"),
                fileConfig.getProperty("db.url"),
                DEFAULT_URL);
        USER = firstNonBlank(System.getenv("FLAVORLOGIC_DB_USER"),
                System.getProperty("flavorlogic.db.user"),
                fileConfig.getProperty("db.user"),
                DEFAULT_USER);
        PASSWORD = firstNonBlank(System.getenv("FLAVORLOGIC_DB_PASSWORD"),
                System.getProperty("flavorlogic.db.password"),
                fileConfig.getProperty("db.password"),
                DEFAULT_PASSWORD);

        String driver = firstNonBlank(System.getProperty("flavorlogic.db.driver"),
                fileConfig.getProperty("db.driver"), DEFAULT_DRIVER);
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            // JDBC 4.0 之后驱动可通过 SPI 自动加载，这里只做一次显式尝试
            LOG.log(Level.WARNING, "未找到 JDBC 驱动类 {0}，将依赖 SPI 自动加载", driver);
        }
        LOG.log(Level.INFO, "数据库配置就绪：{0}", mask(URL));
    }

    private DBUtil() {
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = DBUtil.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "读取 db.properties 失败，将使用默认配置", e);
        }
        return props;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String mask(String url) {
        return url == null ? "" : url.replaceAll("password=[^&]*", "password=***");
    }

    /**
     * 获取数据库连接。
     *
     * @return 新连接
     * @throws SQLException 连接失败
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * 静默关闭若干资源（Connection、Statement、ResultSet 等）。
     *
     * @param resources 需要关闭的资源，允许为空
     */
    public static void close(AutoCloseable... resources) {
        if (resources == null) {
            return;
        }
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (Exception e) {
                LOG.log(Level.FINE, "关闭资源失败", e);
            }
        }
    }

    /**
     * 事务回滚（静默）。
     *
     * @param connection 连接，允许为空
     */
    public static void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
        } catch (SQLException e) {
            LOG.log(Level.FINE, "回滚事务失败", e);
        }
    }

    /**
     * 当前使用的 JDBC 地址（用于管理端排查，已去除敏感参数）。
     *
     * @return 脱敏后的连接地址
     */
    public static String describeUrl() {
        return mask(URL);
    }

    /**
     * 当前使用的数据库账号。
     *
     * @return 数据库用户名
     */
    public static String describeUser() {
        return USER;
    }
}
