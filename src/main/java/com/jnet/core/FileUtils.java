package com.jnet.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/**
 * 文件工具类
 * 替代 ff.jnezha.jnt.utils.FileUtils
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class FileUtils {
    private static final long DEFAULT_MAX_FILE_SIZE = 16L * 1024 * 1024;

    private FileUtils() {}

    /**
     * 从文件读取Base64编码
     * 限制文件大小以避免OOM（默认16MB）
     */
    public static String getBase64FromFile(String filePath) {
        return getBase64FromFile(filePath, DEFAULT_MAX_FILE_SIZE);
    }

    static String getBase64FromFile(File file) {
        return getBase64FromFile(file, DEFAULT_MAX_FILE_SIZE);
    }

    /**
     * 从文件读取Base64编码（可指定最大文件大小）
     *
     * @param filePath 文件路径
     * @param maxFileSize 最大文件大小（字节）
     * @return Base64编码字符串，如果文件不存在或超过大小限制则返回null
     */
    public static String getBase64FromFile(String filePath, long maxFileSize) {
        if (filePath == null || maxFileSize < 0) {
            return null;
        }
        return getBase64FromFile(new File(filePath), maxFileSize);
    }

    private static String getBase64FromFile(File file, long maxFileSize) {
        if (file == null || maxFileSize < 0 || !file.isFile()) {
            return null;
        }
        try {
            // 检查文件大小
            long fileSize = file.length();
            if (fileSize > maxFileSize) {
                return null;
            }

            // 使用流式编码，避免整个文件加载到内存
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream(initialBase64Capacity(fileSize))) {
                Base64.Encoder encoder = Base64.getEncoder();
                try (OutputStream encodedOut = encoder.wrap(bos)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    long totalBytes = 0;
                    while ((len = fis.read(buffer)) != -1) {
                        if (len > maxFileSize - totalBytes) {
                            return null;
                        }
                        encodedOut.write(buffer, 0, len);
                        totalBytes += len;
                    }
                }
                return bos.toString(StandardCharsets.US_ASCII.name());
            }
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
        if (filePath == null || outputStream == null) {
            return -1;
        }
        try {
            File file = new File(filePath);
            if (!file.isFile()) {
                return -1;
            }

            Base64.Encoder encoder = Base64.getEncoder();
            long totalBytes = 0;

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream encodedOut = encoder.wrap(new NonClosingOutputStream(outputStream))) {
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
        return saveBase64ToFile(base64, filePath, Long.MAX_VALUE);
    }

    /**
     * Streams decoded Base64 to a same-directory temporary file and atomically publishes it.
     * The existing target is left untouched when decoding, size validation, or publication fails.
     *
     * @param base64 Base64-encoded content
     * @param filePath destination path
     * @param maxDecodedBytes maximum decoded byte count
     * @return {@code true} when the complete file was published
     */
    public static boolean saveBase64ToFile(String base64, String filePath, long maxDecodedBytes) {
        if (base64 == null || filePath == null || maxDecodedBytes < 0) {
            return false;
        }
        Path temporary = null;
        try {
            Path target = Paths.get(filePath).toAbsolutePath().normalize();
            Path parent = target.getParent();
            if (parent == null) {
                return false;
            }
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".jnet-base64-", ".tmp");
            try (InputStream encoded = new Base64StringInputStream(base64);
                 InputStream decoded = Base64.getDecoder().wrap(encoded);
                 OutputStream output = Files.newOutputStream(
                         temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = decoded.read(buffer)) != -1) {
                    if (read > maxDecodedBytes - total) {
                        return false;
                    }
                    output.write(buffer, 0, read);
                    total += read;
                }
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // A non-atomic fallback could expose a partial replacement, so fail closed.
                return false;
            }
            temporary = null;
            return true;
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup after a failed decode or publish.
                }
            }
        }
    }

    /** Avoids allocating a second byte array for an already in-memory Base64 string. */
    private static final class Base64StringInputStream extends InputStream {
        private final String value;
        private int position;

        private Base64StringInputStream(String value) {
            this.value = value;
        }

        @Override
        public int read() throws IOException {
            if (position >= value.length()) {
                return -1;
            }
            char current = value.charAt(position++);
            if (current > 0x7f) {
                throw new IOException("Base64 input must contain ASCII characters only");
            }
            return current;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (buffer == null) {
                throw new NullPointerException("buffer");
            }
            if (offset < 0 || length < 0 || length > buffer.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) {
                return 0;
            }
            if (position >= value.length()) {
                return -1;
            }
            int count = Math.min(length, value.length() - position);
            for (int i = 0; i < count; i++) {
                char current = value.charAt(position++);
                if (current > 0x7f) {
                    throw new IOException("Base64 input must contain ASCII characters only");
                }
                buffer[offset + i] = (byte) current;
            }
            return count;
        }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        private NonClosingOutputStream(OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    private static int initialBase64Capacity(long fileSize) {
        if (fileSize <= 0) {
            return 32;
        }
        if (fileSize >= 48 * 1024) {
            return 64 * 1024;
        }
        return (int) ((fileSize + 2) / 3 * 4);
    }
}
