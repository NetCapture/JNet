package com.jnet.core;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 数据转换工具类
 * 替代 ff.jnezha.jnt.utils.DataConver
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class DataConver {
    private static final int DEFAULT_MAX_BYTES = 16 * 1024 * 1024;

    private DataConver() {}

    /**
     * 将InputStream转换为String
     */
    public static String parserInputStreamToString(InputStream inputStream) {
        return parserInputStreamToString(inputStream, DEFAULT_MAX_BYTES);
    }

    /** Converts at most {@code maxBytes}; returns {@code null} on overflow or read failure. */
    public static String parserInputStreamToString(InputStream inputStream, int maxBytes) {
        if (inputStream == null) {
            return null;
        }
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Maximum input size cannot be negative");
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream(Math.min(8192, maxBytes));
        byte[] buffer = new byte[8192];
        int length;
        try {
            while ((length = inputStream.read(buffer)) != -1) {
                if (length > maxBytes - result.size()) {
                    return null;
                }
                result.write(buffer, 0, length);
            }
            return result.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
