package com.jnet.multipart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.http.HttpRequest;

/**
 * Multipart 请求体构建器
 * 实现了流式处理，避免大文件加载到内存
 */
public class MultipartBody {
    private final List<Part> parts;
    private final String boundary;

    private MultipartBody(Builder builder) {
        this.parts = new ArrayList<>(builder.parts);
        this.boundary = builder.boundary != null ? builder.boundary : generateBoundary();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private static String generateBoundary() {
        return "JNet-" + UUID.randomUUID().toString();
    }

    public String getBoundary() {
        return boundary;
    }

    public String getContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    /**
     * 转换为 BodyPublisher
     * 将所有 Part 组合成一个输入流序列
     */
    public HttpRequest.BodyPublisher toBodyPublisher() {
        return HttpRequest.BodyPublishers.ofInputStream(this::createInputStream);
    }

    private InputStream createInputStream() {
        Enumeration<InputStream> enumeration = new Enumeration<>() {
            private int index = 0;
            private int state = 0; // 0: boundary, 1: part content, 2: newline, 3: end boundary

            @Override
            public boolean hasMoreElements() {
                return index < parts.size() || state == 3;
            }

            @Override
            public InputStream nextElement() {
                if (state == 3) {
                    state = 4; // Done
                    return new java.io.ByteArrayInputStream(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }

                if (index >= parts.size()) {
                    throw new NoSuchElementException();
                }

                Part part = parts.get(index);
                InputStream stream;

                switch (state) {
                    case 0: // Boundary + Headers
                        String header = "--" + boundary + "\r\n" + part.getHeaders();
                        stream = new java.io.ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8));
                        state = 1;
                        break;
                    case 1: // Content
                        try {
                            stream = part.getInputStream();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to open part stream", e);
                        }
                        state = 2;
                        break;
                    case 2: // Newline after content
                        stream = new java.io.ByteArrayInputStream("\r\n".getBytes(StandardCharsets.UTF_8));
                        index++;
                        state = index < parts.size() ? 0 : 3;
                        break;
                    default:
                        throw new NoSuchElementException();
                }
                return stream;
            }
        };

        return new SequenceInputStream(enumeration);
    }

    public static class Builder {
        private final List<Part> parts = new ArrayList<>();
        private String boundary;

        public Builder addFormField(String name, String value) {
            parts.add(new FormPart(name, value));
            return this;
        }

        public Builder addFilePart(String name, File file) {
            parts.add(new FilePart(name, file));
            return this;
        }

        public Builder addFilePart(String name, String filename, byte[] content, String contentType) {
            parts.add(new FilePart(name, filename, content, contentType));
            return this;
        }

        public Builder boundary(String boundary) {
            this.boundary = boundary;
            return this;
        }

        public MultipartBody build() {
            if (parts.isEmpty()) {
                throw new IllegalStateException("Multipart body must have at least one part.");
            }
            return new MultipartBody(this);
        }
    }
}
