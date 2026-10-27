package com.huaweicloud.hdgatewayservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.huaweicloud.hdgatewayservice.config.GatewayConfig;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class Masker {

    private static final int MAX_LEN = 2000;

    private final GatewayConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public Masker(GatewayConfig config) {
        this.config = config;
    }

    public String mask(String text) {
        if (text == null || text.isEmpty()) return "";
        return truncate(maskText(stripControlChars(text)));
    }

    /**
     * 移除 C0 控制字符（{@code \u0000}~{@code \u001F} 及 {@code \u007F}），
     * 防止 ContentCachingResponseWrapper 读取响应体时带入的 null 填充等
     * 二进制字符污染日志文件，导致 grep 将文件判定为二进制。
     */
    private static String stripControlChars(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public String maskQuery(String query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                sb.append(pair);
            } else {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                sb.append(key).append('=').append(isSensitive(key) ? partial(value) : value);
            }
            sb.append('&');
        }
        if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private String maskText(String text) {
        try {
            JsonNode root = mapper.readTree(text);
            return maskNode(root).toString();
        } catch (Exception e) {
            return text;
        }
    }

    private JsonNode maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if (isSensitive(entry.getKey())) {
                    out.set(entry.getKey(), TextNode.valueOf(maskValue(entry.getValue())));
                } else {
                    out.set(entry.getKey(), maskNode(entry.getValue()));
                }
            });
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            node.forEach(n -> out.add(maskNode(n)));
            return out;
        }
        if (node.isTextual()) {
            String masked = maskUrl(node.asText());
            if (masked != null) return TextNode.valueOf(masked);
        }
        return node;
    }

    private String maskValue(JsonNode value) {
        String s = value.asText();
        String masked = maskUrl(s);
        if (masked != null) return masked;
        return partial(s);
    }

    private String partial(String s) {
        if (s == null) return "";
        int len = s.length();
        if (len <= 2) return "***";
        int keep = Math.max(1, (int) Math.round(len * 0.2));
        if (2 * keep >= len) keep = Math.max(1, len / 3);
        return s.substring(0, keep) + "*".repeat(len - 2 * keep) + s.substring(len - keep);
    }

    private String maskUrl(String s) {
        URI uri;
        try {
            uri = new URI(s);
        } catch (URISyntaxException e) {
            return null;
        }
        if (uri.getScheme() == null) return null;
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx >= 0 && isSensitive(pair.substring(0, idx))) {
                sb.append(pair, 0, idx + 1).append(partial(pair.substring(idx + 1)));
                changed = true;
            } else {
                sb.append(pair);
            }
            sb.append('&');
        }
        if (!changed) return null;
        sb.setLength(sb.length() - 1);
        return s.substring(0, s.indexOf('?') + 1) + sb;
    }

    private boolean isSensitive(String key) {
        if (key == null) return false;
        String k = key.toLowerCase();
        for (String kw : config.maskKeys()) {
            if (kw.length() >= 3) {
                if (k.contains(kw)) return true;
            } else {
                if (k.equals(kw) || k.startsWith(kw + "_") || k.endsWith("_" + kw)
                        || k.contains("." + kw) || k.contains(kw + ".")) return true;
            }
        }
        return false;
    }

    private String truncate(String s) {
        if (s.length() <= MAX_LEN) return s;
        return s.substring(0, MAX_LEN) + "...(truncated)";
    }
}
