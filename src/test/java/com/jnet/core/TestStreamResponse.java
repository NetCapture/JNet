package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamResponse Tests")
class TestStreamResponse {

    @Test
    @DisplayName("StreamResponse: 逐行读取")
    void testReadLines() throws IOException {
        String content = "Line 1\nLine 2\nLine 3";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
        
        List<String> lines = new ArrayList<>();
        try (StreamResponse stream = new StreamResponse(null, inputStream)) {
            stream.readLines(lines::add);
        }
        
        assertEquals(3, lines.size());
        assertEquals("Line 1", lines.get(0));
        assertEquals("Line 2", lines.get(1));
        assertEquals("Line 3", lines.get(2));
    }

    @Test
    @DisplayName("StreamResponse: Iterator 支持")
    void testIterator() throws IOException {
        String content = "A\nB\nC";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
        
        List<String> lines = new ArrayList<>();
        try (StreamResponse stream = new StreamResponse(null, inputStream)) {
            for (String line : stream) {
                lines.add(line);
            }
        }
        
        assertEquals(3, lines.size());
        assertEquals("A", lines.get(0));
    }

    @Test
    @DisplayName("StreamResponse: readLine 单行读取")
    void testReadLine() throws IOException {
        String content = "First\nSecond";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
        
        try (StreamResponse stream = new StreamResponse(null, inputStream)) {
            assertEquals("First", stream.readLine());
            assertEquals("Second", stream.readLine());
            assertNull(stream.readLine());
        }
    }

    @Test
    @DisplayName("StreamResponse: read 字节读取")
    void testReadBytes() throws IOException {
        byte[] content = "Hello World".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
        
        try (StreamResponse stream = new StreamResponse(null, inputStream)) {
            byte[] result = stream.read(5);
            assertEquals("Hello", new String(result, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("StreamResponse: readAll 读取全部")
    void testReadAll() throws IOException {
        String content = "Line 1\nLine 2\nLine 3";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());
        
        try (StreamResponse stream = new StreamResponse(null, inputStream)) {
            String result = stream.readAll();
            assertTrue(result.contains("Line 1"));
            assertTrue(result.contains("Line 2"));
            assertTrue(result.contains("Line 3"));
        }
    }

    @Test
    @DisplayName("StreamResponse: 关闭后抛出异常")
    void testClosedStreamThrows() throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("test".getBytes());
        StreamResponse stream = new StreamResponse(null, inputStream);
        
        stream.close();
        assertTrue(stream.isClosed());
        
        assertThrows(IllegalStateException.class, stream::readLine);
    }

    @Test
    @DisplayName("StreamResponse: null InputStream 抛出异常")
    void testNullInputStream() {
        assertThrows(IllegalArgumentException.class, () -> {
            new StreamResponse(null, null);
        });
    }

    @Test
    @DisplayName("StreamResponse: 空内容处理")
    void testEmptyContent() throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);
        
        try (StreamResponse stream = new StreamResponse(null, inputStream)) {
            assertNull(stream.readLine());
            assertEquals("", stream.readAll());
        }
    }
}
