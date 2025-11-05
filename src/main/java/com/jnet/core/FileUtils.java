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
     * 限制文件大小以避免OOM（默认16MB）
     */
    public static String getBase64FromFile(String filePath) {
        return getBase64FromFile(filePath, 16 * 1024 * 1024); // 默认16MB限制
    }

    /**
     * 从文件读取Base64编码（可指定最大文件大小）
     *
     * @param filePath 文件路径
     * @param maxFileSize 最大文件大小（字节）
     * @return Base64编码字符串，如果文件不存在或超过大小限制则返回null
     */
    public static String getBase64FromFile(String filePath, long maxFileSize) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return null;
            }

            // 检查文件大小
            long fileSize = file.length();
            if (fileSize > maxFileSize) {
                System.err.println("Warning: File too large (" + fileSize + " bytes > " + maxFileSize + " bytes). " +
                                   "Use getBase64FromFileStream() for large files.");
                return null;
            }

            // 使用流式编码，避免整个文件加载到内存
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                Base64.Encoder encoder = Base64.getEncoder();
                try (OutputStream encodedOut = encoder.wrap(bos)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        encodedOut.write(buffer, 0, len);
                    }
                }
                return bos.toString("UTF-8");
            }
        } catch (OutOfMemoryError e) {
            throw new RuntimeException("File too large to encode to Base64: " + filePath + ". " +
                                       "Max allowed: " + (maxFileSize / 1024 / 1024) + "MB", e);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将文件编码为Base64并写入输出流（适用于大文件）
     * 调用者负责关闭输出流
     *
     * @param filePath 文件路径
     * @param outputStream 输出流
     * @return 编码的字节数，失败返回-1
     */
    public static long getBase64FromFileStream(String filePath, OutputStream outputStream) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return -1;
            }

            Base64.Encoder encoder = Base64.getEncoder();
            long totalBytes = 0;

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream encodedOut = encoder.wrap(outputStream)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    encodedOut.write(buffer, 0, len);
                    totalBytes += len;
                }
                encodedOut.flush();
            }

            return totalBytes;
        } catch (Exception e) {
            return -1;
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
