package com.flavorlogic.common.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

/** 对外部采集 URL 做保守校验，阻止直连本机、内网和 IP 字面量。 */
public final class PublicUrlGuard {

    private PublicUrlGuard() {
    }

    public static URI requirePublicHttpUrl(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || host == null || host.isEmpty() || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IOException("采集地址必须是公开的 HTTP/HTTPS 地址");
            }
            String lower = host.toLowerCase(java.util.Locale.ROOT);
            if ("localhost".equals(lower) || lower.endsWith(".localhost") || lower.endsWith(".local")
                    || lower.endsWith(".internal") || lower.matches("[0-9.]+") || lower.contains(":")) {
                throw new IOException("不允许采集本机、内网或 IP 字面量地址");
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IOException("采集域名没有可用地址");
            }
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || isReserved(address.getAddress())) {
                    throw new IOException("采集域名解析到本机、内网或保留地址");
                }
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IOException("采集地址格式不合法", e);
        }
    }

    private static boolean isReserved(byte[] bytes) {
        if (bytes.length != 4) {
            return false;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0 || first == 10 || first == 127 || first >= 224
                || (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168) || (first == 100 && second >= 64 && second <= 127);
    }
}
