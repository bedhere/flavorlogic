package com.flavorlogic.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 受控的一次性公开文档获取器。
 *
 * <p>只负责单 URL、有限大小和有限超时的下载，不负责调度，也不会递归跟踪链接。
 * 调用方必须先完成来源站点白名单和 robots 策略检查。</p>
 */
public final class PublicDocumentFetcher {

    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "FlavorLogicResearchBot/1.0";

    private PublicDocumentFetcher() {
    }

    public static FetchedDocument fetch(String address) throws IOException {
        PublicUrlGuard.requirePublicHttpUrl(address);
        URL url = new URL(address);
        if (!("http".equalsIgnoreCase(url.getProtocol()) || "https".equalsIgnoreCase(url.getProtocol()))) {
            throw new IOException("只允许 HTTP 或 HTTPS 地址");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.1");
        int code = connection.getResponseCode();
        if (code >= 300 && code < 400) {
            throw new IOException("检测到重定向，出于安全原因不会自动跟随");
        }
        if (code < 200 || code >= 300) {
            throw new IOException("来源返回 HTTP " + code);
        }
        String contentType = connection.getContentType();
        if (contentType != null && !isTextContent(contentType)) {
            throw new IOException("来源不是文本内容：" + contentType);
        }
        int declaredLength = connection.getContentLength();
        if (declaredLength > MAX_BYTES) {
            throw new IOException("正文超过 2 MB 上限");
        }
        byte[] bytes;
        try (InputStream in = connection.getInputStream()) {
            bytes = readBounded(in, MAX_BYTES);
        } finally {
            connection.disconnect();
        }
        Charset charset = charsetOf(contentType);
        String raw = new String(bytes, charset);
        String content = isHtml(contentType) ? TextUtil.stripHtml(raw) : raw.trim();
        content = content.replaceAll("\\s+", " ").trim();
        if (content.isEmpty()) {
            throw new IOException("来源正文为空");
        }
        String title = titleOf(raw);
        if (TextUtil.isBlank(title)) {
            title = url.getHost() + url.getPath();
        }
        return new FetchedDocument(title.trim(), content, TextUtil.truncate(content, 300),
                TextUtil.sha256Hex(title.trim() + "\n" + content));
    }

    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("正文超过 " + maxBytes + " 字节上限");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static boolean isTextContent(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        return lower.startsWith("text/") || lower.contains("json") || lower.contains("xml")
                || lower.contains("javascript");
    }

    private static boolean isHtml(String type) {
        return type == null || type.toLowerCase(Locale.ROOT).contains("html");
    }

    private static Charset charsetOf(String type) {
        if (type != null) {
            Matcher matcher = Pattern.compile("(?i)charset=([\\w-]+)").matcher(type);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1));
                } catch (RuntimeException ignored) {
                    // 使用 UTF-8 兜底，避免异常编码阻断审核队列。
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String titleOf(String raw) {
        Matcher matcher = TITLE.matcher(raw);
        return matcher.find() ? TextUtil.stripHtml(matcher.group(1)) : null;
    }

    /** 获取后的规范化文档。 */
    public static final class FetchedDocument {
        private final String title;
        private final String content;
        private final String summary;
        private final String contentHash;

        private FetchedDocument(String title, String content, String summary, String contentHash) {
            this.title = title;
            this.content = content;
            this.summary = summary;
            this.contentHash = contentHash;
        }

        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getSummary() { return summary; }
        public String getContentHash() { return contentHash; }
    }
}
