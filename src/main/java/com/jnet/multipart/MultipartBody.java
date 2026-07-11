package com.jnet.multipart;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Multipart 请求体构建器
 * 实现了流式处理，避免大文件加载到内存
 */
public class MultipartBody {
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private final List<PartSnapshot> parts;
    private final String boundary;
    private final long length;

    private MultipartBody(Builder builder) {
        List<PartSnapshot> snapshots = new ArrayList<>(builder.parts.size());
        for (Part part : builder.parts) {
            snapshots.add(new PartSnapshot(part));
        }
        this.parts = Collections.unmodifiableList(snapshots);
        this.boundary = MultipartValidation.requireBoundary(
                builder.boundary != null ? builder.boundary : generateBoundary());
        this.length = calculateLength();
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
        return "multipart/form-data; boundary="
                + (MultipartValidation.isHttpToken(boundary) ? boundary : '"' + boundary + '"');
    }

    /**
     * Returns the complete encoded body length, or {@code -1} if a custom part
     * has an unknown length or the total would overflow a {@code long}.
     */
    public long getLength() {
        return length;
    }

    /** Opens a fresh stream for the complete multipart body. */
    public InputStream getInputStream() {
        return createInputStream();
    }

    /**
     * 转换为 BodyPublisher
     * 将所有 Part 组合成一个输入流序列
     */
    public HttpRequest.BodyPublisher toBodyPublisher() {
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofInputStream(this::getInputStream);
        return length >= 0
                ? HttpRequest.BodyPublishers.fromPublisher(publisher, length)
                : publisher;
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
                    return bytes("--" + boundary + "--\r\n");
                }

                if (index >= parts.size()) {
                    throw new NoSuchElementException();
                }

                PartSnapshot part = parts.get(index);
                InputStream stream;

                switch (state) {
                    case 0: // Boundary + Headers
                        String header = "--" + boundary + "\r\n" + part.headers;
                        stream = bytes(header);
                        state = 1;
                        break;
                    case 1: // Content
                        try {
                            stream = part.openStream();
                        } catch (IOException e) {
                            throw new UncheckedIOException("Failed to open multipart part stream", e);
                        }
                        state = 2;
                        break;
                    case 2: // Newline after content
                        stream = new ByteArrayInputStream(CRLF);
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

    private long calculateLength() {
        long total = bytesLength("--" + boundary + "--\r\n");
        for (PartSnapshot part : parts) {
            long partLength = part.length;
            if (partLength < 0) {
                return -1;
            }
            total = addLength(total, bytesLength("--" + boundary + "\r\n"));
            total = addLength(total, bytesLength(part.headers));
            total = addLength(total, partLength);
            total = addLength(total, CRLF.length);
            if (total < 0) {
                return -1;
            }
        }
        return total;
    }

    private static long addLength(long left, long right) {
        return left < 0 || right < 0 || left > Long.MAX_VALUE - right ? -1 : left + right;
    }

    private static int bytesLength(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class PartSnapshot {
        private final Part part;
        private final String headers;
        private final long length;

        private PartSnapshot(Part part) {
            this.part = part;
            this.headers = java.util.Objects.requireNonNull(
                    part.getHeaders(), "Multipart part headers cannot be null");
            this.length = part.getLength();
            if (length < -1) {
                throw new IllegalArgumentException("Multipart part length must be -1 or non-negative");
            }
        }

        private InputStream openStream() throws IOException {
            InputStream stream = part.getInputStream();
            if (stream == null) {
                throw new IOException("Multipart part returned a null stream");
            }
            return length < 0 ? stream : new ExactLengthInputStream(stream, length);
        }
    }

    private static final class ExactLengthInputStream extends FilterInputStream {
        private long remaining;

        private ExactLengthInputStream(InputStream input, long length) {
            super(input);
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return verifyEnd();
            }
            int value = super.read();
            if (value < 0) {
                throw new EOFException("Multipart part ended before its declared length");
            }
            remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return verifyEnd();
            }
            int allowed = (int) Math.min(remaining, length);
            int read = super.read(buffer, offset, allowed);
            if (read < 0) {
                throw new EOFException("Multipart part ended before its declared length");
            }
            remaining -= read;
            return read;
        }

        private int verifyEnd() throws IOException {
            if (super.read() >= 0) {
                throw new IOException("Multipart part exceeded its declared length");
            }
            return -1;
        }
    }

    public static class Builder {
        private final List<Part> parts = new ArrayList<>();
        private String boundary;

        public Builder addPart(Part part) {
            if (part == null) {
                throw new IllegalArgumentException("Multipart part cannot be null");
            }
            parts.add(part);
            return this;
        }

        public Builder addFormField(String name, String value) {
            return addPart(new FormPart(name, value));
        }

        public Builder addFilePart(String name, File file) {
            return addPart(new FilePart(name, file));
        }

        public Builder addFilePart(String name, File file, String contentType) {
            return addPart(new FilePart(name, file, contentType));
        }

        public Builder addFilePart(String name, String filename, byte[] content, String contentType) {
            return addPart(new FilePart(name, filename, content, contentType));
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
