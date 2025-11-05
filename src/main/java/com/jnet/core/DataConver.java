package com.jnet.core;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/**
 * 数据转换工具类
 * 替代 ff.jnezha.jnt.utils.DataConver
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class DataConver {

    private DataConver() {}

    /**
     * 将InputStream转换为String
     */
    public static String parserInputStreamToString(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        try {
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            return result.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
