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
    private final InputStream inputStream;
    private final Response response;
    private final Charset charset;
    private BufferedReader reader;
    private boolean closed = false;

    public StreamResponse(Response response, InputStream inputStream) {
        this(response, inputStream, StandardCharsets.UTF_8);
    }

    public StreamResponse(Response response, InputStream inputStream, Charset charset) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        this.response = response;
        this.inputStream = inputStream;
        this.charset = charset;
        this.reader = new BufferedReader(new InputStreamReader(inputStream, charset));
    }

    /**
     * 逐行读取响应，使用回调处理每一行
     */
    public void readLines(Consumer<String> lineConsumer) throws IOException {
        checkClosed();
        
        String line;
        while ((line = reader.readLine()) != null) {
            lineConsumer.accept(line);
        }
    }

    /**
     * 读取一行
     */
    public String readLine() throws IOException {
        checkClosed();
        return reader.readLine();
    }

    /**
     * 读取指定数量的字节
     */
    public byte[] read(int length) throws IOException {
        checkClosed();
        
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
        checkClosed();
        
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        
        return sb.toString();
    }

    /**
     * 获取原始输入流（高级用法）
     */
    public InputStream getInputStream() {
        checkClosed();
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
                        nextLine = reader.readLine();
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
            if (reader != null) {
                reader.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
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
}
