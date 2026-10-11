package com.flavorlogic.util;

import com.flavorlogic.model.SourceSite;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 最小 robots.txt 检查器：用于单 URL 采集前的保守阻断。 */
public final class RobotsPolicy {

    private static final String USER_AGENT = "FlavorLogicResearchBot";
    private static final int MAX_BYTES = 256 * 1024;

    private RobotsPolicy() {
    }

    /**
     * 检查站点是否允许当前采集器访问目标路径。
     * API / DATASET 来源可不登记 robots 地址；普通 WEB 来源缺少 robots 地址时直接阻断。
     */
    public static void ensureAllowed(SourceSite site, String targetUrl) throws IOException {
        if (site == null) {
            throw new IOException("来源站点不存在");
        }
        if (!sameHost(site.getBaseUrl(), targetUrl)) {
            throw new IOException("目标地址不在来源站点根域名内");
        }
        if ("API".equalsIgnoreCase(site.getSourceType()) || "DATASET".equalsIgnoreCase(site.getSourceType())) {
            return;
        }
        if (TextUtil.isBlank(site.getRobotsUrl())) {
            throw new IOException("WEB 来源未登记 robots.txt，已阻止自动获取");
        }
        String robots = download(site.getRobotsUrl());
        String path;
        try {
            path = new URI(targetUrl).getPath();
        } catch (java.net.URISyntaxException e) {
            throw new IOException("目标地址格式不合法", e);
        }
        if (isDisallowed(robots, path)) {
            throw new IOException("robots.txt 禁止访问该路径");
        }
    }

    public static boolean sameHost(String baseUrl, String targetUrl) throws IOException {
        try {
            URI base = URI.create(baseUrl);
            URI target = URI.create(targetUrl);
            String baseHost = base.getHost();
            String targetHost = target.getHost();
            return baseHost != null && targetHost != null && baseHost.equalsIgnoreCase(targetHost)
                    && ("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()));
        } catch (RuntimeException e) {
            throw new IOException("地址格式不合法", e);
        }
    }

    private static String download(String address) throws IOException {
        PublicUrlGuard.requirePublicHttpUrl(address);
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("robots.txt 返回 HTTP " + code);
        }
        try (InputStream in = connection.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IOException("robots.txt 超过大小上限");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    static boolean isDisallowed(String content, String path) {
        List<String> rules = new ArrayList<>();
        List<String> agents = new ArrayList<>();
        boolean hasRules = false;
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.split("#", 2)[0].trim();
            if (line.isEmpty()) {
                if (hasRules) {
                    agents.clear();
                    hasRules = false;
                }
                continue;
            }
            if (!line.contains(":")) {
                continue;
            }
            String key = line.substring(0, line.indexOf(':')).trim();
            String value = line.substring(line.indexOf(':') + 1).trim();
            if ("user-agent".equalsIgnoreCase(key)) {
                if (hasRules) {
                    agents.clear();
                    hasRules = false;
                }
                agents.add(value);
            } else if ("disallow".equalsIgnoreCase(key) || "allow".equalsIgnoreCase(key)) {
                hasRules = true;
                if ("disallow".equalsIgnoreCase(key) && !value.isEmpty() && appliesToBot(agents)) {
                    rules.add(value);
                }
            }
        }
        for (String rule : rules) {
            if (path != null && matches(path, rule)) {
                return true;
            }
        }
        return false;
    }

    private static boolean appliesToBot(List<String> agents) {
        for (String agent : agents) {
            if ("*".equals(agent) || USER_AGENT.equalsIgnoreCase(agent)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String path, String rule) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < rule.length(); i++) {
            char c = rule.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '$' && i == rule.length() - 1) {
                regex.append('$');
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return java.util.regex.Pattern.compile(regex.toString()).matcher(path).find();
    }
}
