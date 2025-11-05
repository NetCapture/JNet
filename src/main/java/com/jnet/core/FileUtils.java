package com.jnet.core;

import java.io.*;
import java.util.Base64;

/**
 * 文件工具类
 * 替代 ff.jnezha.jnt.utils.FileUtils
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class FileUtils {

    private FileUtils() {}

    /**
     * 从文件读取Base64编码
     */
    public static String getBase64FromFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return null;
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }
                byte[] fileBytes = bos.toByteArray();
                return Base64.getEncoder().encodeToString(fileBytes);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将Base64内容写入文件
     */
    public static boolean saveBase64ToFile(String base64, String filePath) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(bytes);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
