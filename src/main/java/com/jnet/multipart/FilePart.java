package com.jnet.multipart;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 文件上传部分
 */
public class FilePart implements Part {
    private final String headers;
    private final File file;
    private final byte[] bytes;
    private final long length;

    public FilePart(String name, File file) {
        this(name, file, null);
    }

    public FilePart(String name, File file, String contentType) {
        validateFile(file);
        String resolvedContentType = contentType != null ? contentType : detectContentType(file);
        this.headers = createHeaders(name, file.getName(), resolvedContentType);
        this.file = file;
        this.bytes = null;
        this.length = file.length();
    }

    public FilePart(String name, String filename, byte[] content, String contentType) {
        if (content == null) {
            throw new IllegalArgumentException("File content cannot be null");
        }
        String resolvedContentType = contentType != null ? contentType : "application/octet-stream";
        this.headers = createHeaders(name, filename, resolvedContentType);
        this.file = null;
        this.bytes = content.clone();
        this.length = content.length;
    }

    private static String createHeaders(String name, String filename, String contentType) {
        String safeName = MultipartValidation.quoteParameter(name, "File field name");
        String safeFilename = MultipartValidation.quoteParameter(filename, "Filename");
        String safeContentType = MultipartValidation.requireHeaderValue(contentType, "Content type").trim();
        if (safeContentType.isEmpty()) {
            throw new IllegalArgumentException("Content type cannot be empty");
        }
        return "Content-Disposition: form-data; name=\"" + safeName
                + "\"; filename=\"" + safeFilename
                + "\"\r\nContent-Type: " + safeContentType + "\r\n\r\n";
    }

    private static void validateFile(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("File does not exist or is not a regular file: " + file);
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException("File is not readable: " + file);
        }
    }

    private static String detectContentType(File file) {
        try {
            String type = Files.probeContentType(file.toPath());
            return type != null ? type : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (file != null) {
            if (file.length() != length) {
                throw new IOException("File size changed after the multipart body was built: " + file);
            }
            return new FileInputStream(file);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public String getHeaders() {
        return headers;
    }
}
