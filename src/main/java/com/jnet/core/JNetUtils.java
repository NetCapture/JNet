package com.jnet.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 精简工具类 - 提供常用的工具方法
 * 替代org.json等重型库，提供最轻量级的功能
 *
 * @author JNet Team
 * @version 3.0
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
            byte[] decoded = Base64.getDecoder().decode(str);
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

    // ========== 简单JSON工具 ==========

    /**
     * 简单的JSON构建器 - 替代org.json
     */
    public static class JsonBuilder {
        private final StringBuilder json = new StringBuilder();

        public JsonBuilder() {
            json.append("{");
        }

        /**
         * 添加字符串字段
         */
        public JsonBuilder add(String key, String value) {
            if (json.length() > 1) {
                json.append(",");
            }
            escapeAndAppend(key).append(":").append(escapeAndAppend(value));
            return this;
        }

        /**
         * 添加数字字段
         */
        public JsonBuilder add(String key, Number value) {
            if (json.length() > 1) {
                json.append(",");
            }
            escapeAndAppend(key).append(":").append(value);
            return this;
        }

        /**
         * 添加布尔字段
         */
        public JsonBuilder add(String key, Boolean value) {
            if (json.length() > 1) {
                json.append(",");
            }
            escapeAndAppend(key).append(":").append(value);
            return this;
        }

        /**
         * 添加null值
         */
        public JsonBuilder addNull(String key) {
            if (json.length() > 1) {
                json.append(",");
            }
            escapeAndAppend(key).append(":null");
            return this;
        }

        /**
         * 转义并追加字符串
         */
        private StringBuilder escapeAndAppend(String str) {
            if (str == null) {
                return new StringBuilder("null");
            }

            json.append("\"");
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                switch (c) {
                    case '"':
                        json.append("\\\"");
                        break;
                    case '\\':
                        json.append("\\\\");
                        break;
                    case '\b':
                        json.append("\\b");
                        break;
                    case '\f':
                        json.append("\\f");
                        break;
                    case '\n':
                        json.append("\\n");
                        break;
                    case '\r':
                        json.append("\\r");
                        break;
                    case '\t':
                        json.append("\\t");
                        break;
                    default:
                        json.append(c);
                        break;
                }
            }
            json.append("\"");
            return json;
        }

        /**
         * 构建JSON字符串
         */
        public String build() {
            json.append("}");
            return json.toString();
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
        private final long startTime;

        public StopWatch() {
            this.startTime = System.currentTimeMillis();
        }

        /**
         * 获取已耗时（毫秒）
         */
        public long getElapsed() {
            return System.currentTimeMillis() - startTime;
        }

        /**
         * 重置计时器
         */
        public void reset() {
            // 重新创建新的StopWatch实例
        }

        @Override
        public String toString() {
            return "StopWatch{" +
                    "elapsed=" + getElapsed() +
                    "ms}";
        }
    }
}
