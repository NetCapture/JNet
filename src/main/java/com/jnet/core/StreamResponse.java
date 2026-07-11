package com.jnet.core;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * 流式响应处理器
 * 支持逐行读取、chunked 传输、大响应体处理
 * 
 * 设计原则：
 * - 不将整个响应体加载到内存
 * - 支持惰性迭代
 * - 自动资源管理
 */
public class StreamResponse implements Closeable, Iterable<String> {
    private static final int DEFAULT_MAX_READ_ALL_CHARS = 64 * 1024 * 1024;
    private static final int DEFAULT_MAX_LINE_CHARS = 1024 * 1024;

    private final InputStream inputStream;
    private final Response response;
    private final Charset charset;
    private BufferedReader reader;
    private boolean closed = false;
    private Mode mode;

    public StreamResponse(Response response, InputStream inputStream) {
        this(response, inputStream, StandardCharsets.UTF_8);
    }

    public StreamResponse(Response response, InputStream inputStream, Charset charset) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        this.response = response;
        this.inputStream = inputStream;
        this.charset = java.util.Objects.requireNonNull(charset, "charset");
        this.reader = new BufferedReader(new InputStreamReader(inputStream, charset));
    }

    /**
     * 逐行读取响应，使用回调处理每一行
     */
    public void readLines(Consumer<String> lineConsumer) throws IOException {
        readLines(lineConsumer, DEFAULT_MAX_LINE_CHARS);
    }

    /** Reads lines while rejecting any individual line larger than {@code maxLineChars}. */
    public void readLines(Consumer<String> lineConsumer, int maxLineChars) throws IOException {
        checkClosed();
        use(Mode.TEXT);
        java.util.Objects.requireNonNull(lineConsumer, "lineConsumer");

        String line;
        while ((line = readLineBounded(maxLineChars)) != null) {
            lineConsumer.accept(line);
        }
    }

    /**
     * 读取一行
     */
    public String readLine() throws IOException {
        return readLine(DEFAULT_MAX_LINE_CHARS);
    }

    /** Reads one line while bounding the returned character count. */
    public String readLine(int maxLineChars) throws IOException {
        checkClosed();
        use(Mode.TEXT);
        return readLineBounded(maxLineChars);
    }

    /**
     * 读取指定数量的字节
     */
    public byte[] read(int length) throws IOException {
        checkClosed();
        use(Mode.BINARY);
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        
        byte[] buffer = new byte[length];
        int totalRead = 0;
        
        while (totalRead < length) {
            int read = inputStream.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                break;
            }
            totalRead += read;
        }
        
        if (totalRead < length) {
            // 实际读取的数据少于请求的长度，调整数组大小
            byte[] result = new byte[totalRead];
            System.arraycopy(buffer, 0, result, 0, totalRead);
            return result;
        }
        
        return buffer;
    }

    /**
     * 读取所有剩余内容到字符串
     * 注意：大响应体会消耗大量内存
     */
    public String readAll() throws IOException {
        return readAll(DEFAULT_MAX_READ_ALL_CHARS);
    }

    /** Reads all remaining text while bounding the normalized output size. */
    public String readAll(int maxChars) throws IOException {
        checkClosed();
        use(Mode.TEXT);
        if (maxChars < 0) {
            throw new IllegalArgumentException("Maximum character count cannot be negative");
        }

        StringBuilder result = new StringBuilder(Math.min(8192, maxChars));
        boolean sawContent = false;
        boolean endedWithLineBreak = false;
        int current;
        while ((current = reader.read()) != -1) {
            sawContent = true;
            if (current == '\r') {
                reader.mark(1);
                int next = reader.read();
                if (next != '\n' && next != -1) {
                    reader.reset();
                }
                appendBounded(result, '\n', maxChars);
                endedWithLineBreak = true;
            } else if (current == '\n') {
                appendBounded(result, '\n', maxChars);
                endedWithLineBreak = true;
            } else {
                appendBounded(result, (char) current, maxChars);
                endedWithLineBreak = false;
            }
        }
        if (sawContent && !endedWithLineBreak) {
            appendBounded(result, '\n', maxChars);
        }
        return result.toString();
    }

    /**
     * 获取原始输入流（高级用法）
     */
    public InputStream getInputStream() {
        checkClosed();
        use(Mode.BINARY);
        return inputStream;
    }

    /**
     * 获取关联的响应对象
     */
    public Response getResponse() {
        return response;
    }

    /**
     * 获取字符编码
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * 迭代器支持 - 允许 for-each 循环
     */
    @Override
    public Iterator<String> iterator() {
        checkClosed();
        use(Mode.TEXT);
        
        return new Iterator<String>() {
            private String nextLine;
            private boolean nextLineRead = false;

            @Override
            public boolean hasNext() {
                if (closed) {
                    return false;
                }
                
                if (!nextLineRead) {
                    try {
                        nextLine = readLineBounded(DEFAULT_MAX_LINE_CHARS);
                        nextLineRead = true;
                    } catch (IOException e) {
                        throw new RuntimeException("Error reading line", e);
                    }
                }
                return nextLine != null;
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                nextLineRead = false;
                return nextLine;
            }
        };
    }

    /**
     * 关闭流并释放资源
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            reader.close();
        }
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("StreamResponse is closed");
        }
    }

    /**
     * 检查流是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }

    private void use(Mode requested) {
        if (mode != null && mode != requested) {
            throw new IllegalStateException("Cannot mix text and binary reads on the same StreamResponse");
        }
        mode = requested;
    }

    private static void appendBounded(StringBuilder output, char value, int maxChars) throws IOException {
        if (output.length() >= maxChars) {
            throw new IOException("Stream response exceeds maximum size of " + maxChars + " characters");
        }
        output.append(value);
    }

    private String readLineBounded(int maxLineChars) throws IOException {
        if (maxLineChars < 0) {
            throw new IllegalArgumentException("Maximum line length cannot be negative");
        }
        StringBuilder line = new StringBuilder(Math.min(256, maxLineChars));
        boolean sawCharacter = false;
        int current;
        while ((current = reader.read()) != -1) {
            sawCharacter = true;
            if (current == '\n') {
                return line.toString();
            }
            if (current == '\r') {
                reader.mark(1);
                int next = reader.read();
                if (next != '\n' && next != -1) {
                    reader.reset();
                }
                return line.toString();
            }
            if (line.length() >= maxLineChars) {
                IOException overflow = new IOException(
                        "Stream response line exceeds maximum size of "
                                + maxLineChars + " characters");
                try {
                    close();
                } catch (IOException closeFailure) {
                    overflow.addSuppressed(closeFailure);
                }
                throw overflow;
            }
            line.append((char) current);
        }
        return sawCharacter ? line.toString() : null;
    }

    private enum Mode {
        TEXT,
        BINARY
    }
}
