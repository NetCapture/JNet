package com.jnet.multipart;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multipart 模块测试套件
 * 目标：80%+ 测试覆盖率
 */
@DisplayName("Multipart Tests")
class TestMultipartBody {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("MultipartBody: 构建简单表单字段")
    void testSimpleFormField() throws Exception {
        MultipartBody body = MultipartBody.newBuilder()
                .addFormField("username", "alice")
                .addFormField("email", "alice@example.com")
                .build();

        assertNotNull(body);
        assertNotNull(body.getBoundary());
        assertTrue(body.getContentType().startsWith("multipart/form-data; boundary="));
    }

    @Test
    @DisplayName("MultipartBody: 添加文件部分")
    void testAddFilePart() throws Exception {
        File tempFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(tempFile.toPath(), "Hello World", StandardCharsets.UTF_8);

        MultipartBody body = MultipartBody.newBuilder()
                .addFormField("description", "Test file")
                .addFilePart("file", tempFile)
                .build();

        assertNotNull(body);
        String contentType = body.getContentType();
        assertTrue(contentType.contains("boundary="));
    }

    @Test
    @DisplayName("MultipartBody: 添加字节数组文件")
    void testAddFilePartFromBytes() throws Exception {
        byte[] content = "Binary data".getBytes(StandardCharsets.UTF_8);

        MultipartBody body = MultipartBody.newBuilder()
                .addFilePart("upload", "data.bin", content, "application/octet-stream")
                .build();

        assertNotNull(body);
    }

    @Test
    @DisplayName("MultipartBody: 自定义边界")
    void testCustomBoundary() {
        MultipartBody body = MultipartBody.newBuilder()
                .addFormField("field", "value")
                .boundary("CustomBoundary123")
                .build();

        assertEquals("CustomBoundary123", body.getBoundary());
        assertEquals("multipart/form-data; boundary=CustomBoundary123", body.getContentType());
    }

    @Test
    @DisplayName("MultipartBody: 空 parts 抛出异常")
    void testEmptyPartsThrows() {
        assertThrows(IllegalStateException.class, () -> {
            MultipartBody.newBuilder().build();
        });
    }

    @Test
    @DisplayName("MultipartBody: 转换为 BodyPublisher")
    void testToBodyPublisher() throws Exception {
        MultipartBody body = MultipartBody.newBuilder()
                .addFormField("name", "test")
                .build();

        var publisher = body.toBodyPublisher();
        assertNotNull(publisher);
    }

    @Test
    @DisplayName("MultipartBody: 多个字段和文件")
    void testMultipleFieldsAndFiles() throws Exception {
        File file1 = tempDir.resolve("file1.txt").toFile();
        File file2 = tempDir.resolve("file2.txt").toFile();
        Files.writeString(file1.toPath(), "Content 1");
        Files.writeString(file2.toPath(), "Content 2");

        MultipartBody body = MultipartBody.newBuilder()
                .addFormField("field1", "value1")
                .addFormField("field2", "value2")
                .addFilePart("upload1", file1)
                .addFilePart("upload2", file2)
                .build();

        assertNotNull(body);
    }

    @Test
    @DisplayName("FormPart: 生成正确的头部")
    void testFormPartHeaders() throws Exception {
        FormPart part = new FormPart("username", "alice");
        String headers = part.getHeaders();

        assertTrue(headers.contains("Content-Disposition: form-data"));
        assertTrue(headers.contains("name=\"username\""));
    }

    @Test
    @DisplayName("FormPart: 写入输出流")
    void testFormPartWriteToStream() throws Exception {
        FormPart part = new FormPart("key", "value");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        part.writeTo(out);

        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("value"));
    }

    @Test
    @DisplayName("FilePart: 从文件创建")
    void testFilePartFromFile() throws Exception {
        File file = tempDir.resolve("test.txt").toFile();
        String content = "Test content";
        Files.writeString(file.toPath(), content);

        FilePart part = new FilePart("upload", file);
        String headers = part.getHeaders();

        assertTrue(headers.contains("filename=\"test.txt\""));
        assertTrue(headers.contains("Content-Type:"));
    }

    @Test
    @DisplayName("FilePart: 从字节数组创建")
    void testFilePartFromBytes() throws Exception {
        byte[] content = "Binary content".getBytes(StandardCharsets.UTF_8);
        FilePart part = new FilePart("upload", "data.bin", content, "application/octet-stream");

        String headers = part.getHeaders();
        assertTrue(headers.contains("filename=\"data.bin\""));
        assertTrue(headers.contains("application/octet-stream"));
    }

    @Test
    @DisplayName("FilePart: 写入输出流")
    void testFilePartWriteToStream() throws Exception {
        byte[] content = "File content".getBytes(StandardCharsets.UTF_8);
        FilePart part = new FilePart("file", "test.txt", content, "text/plain");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part.writeTo(out);

        byte[] result = out.toByteArray();
        assertTrue(result.length > content.length); // Headers + content
    }

    @Test
    @DisplayName("MultipartBody: 边界随机生成")
    void testBoundaryGeneration() {
        MultipartBody body1 = MultipartBody.newBuilder()
                .addFormField("field", "value")
                .build();

        MultipartBody body2 = MultipartBody.newBuilder()
                .addFormField("field", "value")
                .build();

        assertNotEquals(body1.getBoundary(), body2.getBoundary());
    }

    @Test
    @DisplayName("MultipartBody: 特殊字符处理")
    void testSpecialCharacters() throws Exception {
        MultipartBody body = MultipartBody.newBuilder()
                .addFormField("name", "用户名")
                .addFormField("email", "test@example.com")
                .build();

        assertNotNull(body);
    }

    @Test
    @DisplayName("MultipartBody: 大文件处理")
    void testLargeFile() throws Exception {
        File largeFile = tempDir.resolve("large.bin").toFile();
        byte[] data = new byte[1024 * 1024]; // 1MB
        Files.write(largeFile.toPath(), data);

        MultipartBody body = MultipartBody.newBuilder()
                .addFilePart("upload", largeFile)
                .build();

        assertNotNull(body.toBodyPublisher());
    }
}
