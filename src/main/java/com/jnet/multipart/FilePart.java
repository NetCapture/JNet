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
    private final Object content; // File or byte[]
    private final long length;

    public FilePart(String name, File file) {
        this(name, file, detectContentType(file));
    }

    public FilePart(String name, File file, String contentType) {
        this.headers = String.format(
                "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n",
                name, file.getName(), contentType != null ? contentType : "application/octet-stream");
        this.content = file;
        this.length = file.length();
    }

    public FilePart(String name, String filename, byte[] content, String contentType) {
        this.headers = String.format(
                "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n",
                name, filename, contentType != null ? contentType : "application/octet-stream");
        this.content = content;
        this.length = content.length;
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
        if (content instanceof File) {
            return new FileInputStream((File) content);
        } else {
            return new ByteArrayInputStream((byte[]) content);
        }
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
