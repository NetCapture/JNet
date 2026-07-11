package com.jnet.core;

import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONObject;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 精简工具类 - 提供常用的工具方法
 * 替代org.json等重型库，提供最轻量级的功能
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class JNetUtils {

    private JNetUtils() {
        // 防止实例化
    }

    // ========== 字符串工具 ==========

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(CharSequence str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否为空白
     */
    public static boolean isBlank(CharSequence str) {
        return str == null || str.toString().trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空白
     */
    public static boolean isNotBlank(CharSequence str) {
        return !isBlank(str);
    }

    /**
     * 去除首尾空白
     */
    public static String trim(String str) {
        if (str == null) {
            return null;
        }
        return str.trim();
    }

    // ========== Base64编解码 ==========

    /**
     * Base64编码
     */
    public static String encodeBase64(String str) {
        if (str == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64解码
     */
    public static String decodeBase64(String str) {
        if (str == null) {
            return null;
        }
        try {
            // Remove all whitespace (newlines, spaces, tabs) from the base64 string
            String cleanStr = str.replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleanStr);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ========== MD5哈希 ==========

    /**
     * 计算MD5哈希
     */
    public static String md5(String str) {
        if (str == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ========== URL构建 ==========

    /**
     * 构建带参数的URL
     * 优化：预分配容量
     */
    public static String buildUrl(String url, Map<String, String> params) {
        if (url == null || url.isEmpty() || params == null || params.isEmpty()) {
            return url;
        }

        int fragmentIndex = url.indexOf('#');
        String base = fragmentIndex >= 0 ? url.substring(0, fragmentIndex) : url;
        String fragment = fragmentIndex >= 0 ? url.substring(fragmentIndex) : "";
        StringBuilder result = new StringBuilder(url.length() + params.size() * 20);
        result.append(base);

        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                if (base.indexOf('?') < 0) {
                    result.append('?');
                } else if (!base.endsWith("?") && !base.endsWith("&")) {
                    result.append('&');
                }
                first = false;
            } else {
                result.append('&');
            }
            result.append(urlEncode(entry.getKey()))
                    .append('=')
                    .append(entry.getValue() == null ? "" : urlEncode(entry.getValue()));
        }
        return result.append(fragment).toString();
    }

    // ========== JSON序列化 ==========

    /**
     * 将对象转换为JSON字符串
     */
    public static String toJsonString(Object obj) {
        StringBuilder sb = new StringBuilder(512);
        toJsonString(obj, 0, new IdentityHashMap<>(), sb);
        return sb.toString();
    }

    /**
     * 内部实现 - 优化版 (使用 StringBuilder)
     */
    private static void toJsonString(Object obj, int depth, IdentityHashMap<Object, Boolean> visited, StringBuilder sb) {
        if (depth > 100) {
            throw new IllegalArgumentException("JSON serialization depth exceeded (max 100)");
        }

        if (obj == null) {
            sb.append("null");
            return;
        }

        // 基本类型快速路径
        if (obj instanceof String) {
            escapeJsonString((String) obj, sb);
            return;
        }
        if (obj instanceof Double || obj instanceof Float) {
            double value = ((Number) obj).doubleValue();
            if (!Double.isFinite(value)) {
                sb.append("null");
            } else {
                sb.append(obj.toString());
            }
            return;
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
            return;
        }
        if (obj instanceof Character) {
            escapeJsonString(obj.toString(), sb);
            return;
        }
        if (obj instanceof JSONObject || obj instanceof JSONArray) {
            sb.append(obj.toString());
            return;
        }

        // 复合类型循环引用检测
        if (visited.containsKey(obj)) {
            throw new IllegalArgumentException("Circular reference detected in JSON serialization");
        }
        visited.put(obj, Boolean.TRUE);

        try {
            // Map 处理
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                if (map.isEmpty()) {
                    sb.append("{}");
                    return;
                }

                sb.append('{');
                boolean first = true;

                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!first) sb.append(',');
                    escapeJsonString(String.valueOf(e.getKey()), sb);
                    sb.append(':');
                    toJsonString(e.getValue(), depth + 1, visited, sb);
                    first = false;
                }
                sb.append('}');
                return;
            }

            // Iterable 处理
            if (obj instanceof Iterable) {
                sb.append('[');
                boolean first = true;

                for (Object item : (Iterable<?>) obj) {
                    if (!first) sb.append(',');
                    toJsonString(item, depth + 1, visited, sb);
                    first = false;
                }
                sb.append(']');
                return;
            }

            // 数组处理
            if (obj.getClass().isArray()) {
                int length = Array.getLength(obj);
                if (length == 0) {
                    sb.append("[]");
                    return;
                }

                sb.append('[');
                for (int i = 0; i < length; i++) {
                    if (i > 0) sb.append(',');
                    toJsonString(Array.get(obj, i), depth + 1, visited, sb);
                }
                sb.append(']');
                return;
            }

            // 日期时间
            if (obj instanceof Date) {
                sb.append('"').append(((Date) obj).toInstant().toString()).append('"');
                return;
            }
            if (obj instanceof Temporal) {
                sb.append('"').append(obj.toString()).append('"');
                return;
            }

            escapeJsonString(obj.toString(), sb);

        } finally {
            visited.remove(obj);
        }
    }

    /**
     * JSON字符串转义
     */
    private static void escapeJsonString(String str, StringBuilder sb) {
        if (str == null) {
            sb.append("null");
            return;
        }

        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c <= 0x1F) {
                        sb.append("\\u00");
                        sb.append(Character.forDigit((c >>> 4) & 0x0f, 16));
                        sb.append(Character.forDigit(c & 0x0f, 16));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ========== 简单JSON工具 ==========

    /**
     * 简单的JSON构建器 - 替代org.json
     */
    public static class JsonBuilder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public JsonBuilder() {
        }

        /**
         * 添加字符串字段
         */
        public JsonBuilder add(String key, String value) {
            values.put(key, value);
            return this;
        }

        /**
         * 添加数字字段
         */
        public JsonBuilder add(String key, Number value) {
            values.put(key, value);
            return this;
        }

        /**
         * 添加布尔字段
         */
        public JsonBuilder add(String key, Boolean value) {
            values.put(key, value);
            return this;
        }

        /**
         * 添加null值
         */
        public JsonBuilder addNull(String key) {
            values.put(key, null);
            return this;
        }

        /**
         * 构建JSON字符串
         */
        public String build() {
            return toJsonString(values);
        }
    }

    /**
     * 创建JSON构建器
     */
    public static JsonBuilder json() {
        return new JsonBuilder();
    }

    // ========== URL工具 ==========

    /**
     * 简单URL编码
     */
    public static String urlEncode(String str) {
        if (str == null) {
            return null;
        }
        try {
            return java.net.URLEncoder.encode(str, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return str;
        }
    }

    /**
     * 简单URL解码
     */
    public static String urlDecode(String str) {
        if (str == null) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(str, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return str;
        }
    }

    // ========== 数字工具 ==========

    /**
     * 安全的整数转换
     */
    public static int toInt(String str, int defaultValue) {
        if (isEmpty(str)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全的Long转换
     */
    public static long toLong(String str, long defaultValue) {
        if (isEmpty(str)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(str);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全的Double转换
     */
    public static double toDouble(String str, double defaultValue) {
        if (isEmpty(str)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ========== 文件大小格式化 ==========

    /**
     * 格式化文件大小
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // ========== 性能计时 ==========

    /**
     * 简单的性能计时器
     */
    public static class StopWatch {
        private volatile long startNanos;

        public StopWatch() {
            reset();
        }

        /**
         * 获取已耗时（毫秒）
         */
        public long getElapsed() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        }

        /**
         * 重置计时器
         */
        public void reset() {
            startNanos = System.nanoTime();
        }

        @Override
        public String toString() {
            return "StopWatch{" + "elapsed=" + getElapsed() + "ms}";
        }
    }
}
