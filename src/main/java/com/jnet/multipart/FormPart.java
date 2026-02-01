package com.jnet.multipart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 表单字段部分
 */
public class FormPart implements Part {
    private final String headers;
    private final byte[] content;

    public FormPart(String name, String value) {
        this.headers = String.format("Content-Disposition: form-data; name=\"%s\"\r\n\r\n", name);
        this.content = value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public long getLength() {
        return content.length;
    }

    @Override
    public String getHeaders() {
        return headers;
    }
}
