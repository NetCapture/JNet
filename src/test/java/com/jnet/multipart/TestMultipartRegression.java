package com.jnet.multipart;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestMultipartRegression {

    @Test
    void serializesARepeatableBodyWithAnExactContentLength() throws Exception {
        MultipartBody body = MultipartBody.newBuilder()
                .boundary("Boundary123")
                .addFormField("name", "value")
                .build();

        byte[] first;
        byte[] second;
        try (InputStream input = body.getInputStream()) {
            first = input.readAllBytes();
        }
        try (InputStream input = body.getInputStream()) {
            second = input.readAllBytes();
        }

        String expected = "--Boundary123\r\n"
                + "Content-Disposition: form-data; name=\"name\"\r\n\r\n"
                + "value\r\n"
                + "--Boundary123--\r\n";
        assertEquals(expected, new String(first, StandardCharsets.UTF_8));
        assertArrayEquals(first, second);
        assertEquals(first.length, body.getLength());
        assertEquals(first.length, body.toBodyPublisher().contentLength());
    }

    @Test
    void rejectsHeaderAndBoundaryInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> new FormPart("field\r\nX-Injected: yes", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> new FilePart("file", "safe.txt\r\nX-Injected: yes",
                        new byte[0], "application/octet-stream"));
        assertThrows(IllegalArgumentException.class,
                () -> new FilePart("file", "safe.txt", new byte[0],
                        "text/plain\r\nX-Injected: yes"));
        assertThrows(IllegalArgumentException.class,
                () -> MultipartBody.newBuilder()
                        .boundary("safe\r\nX-Injected")
                        .addFormField("field", "value")
                        .build());
    }

    @Test
    void escapesQuotedDispositionParameters() {
        FormPart part = new FormPart("a\"b\\c", "value");
        assertTrue(part.getHeaders().contains("name=\"a\\\"b\\\\c\""));
    }

    @Test
    void byteArrayFilePartTakesAnImmutableSnapshot() throws Exception {
        byte[] content = "original".getBytes(StandardCharsets.UTF_8);
        FilePart part = new FilePart("file", "data.bin", content, "application/octet-stream");
        content[0] = 'X';

        try (InputStream input = part.getInputStream()) {
            assertEquals("original", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void customPartMetadataIsSnapshottedWhenTheBodyIsBuilt() throws Exception {
        AtomicInteger headerCalls = new AtomicInteger();
        AtomicInteger lengthCalls = new AtomicInteger();
        Part mutable = new Part() {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public long getLength() {
                return lengthCalls.incrementAndGet() == 1 ? 3 : 99;
            }

            @Override
            public String getHeaders() {
                return headerCalls.incrementAndGet() == 1
                        ? "X-Part: original\r\n\r\n"
                        : "X-Part: changed\r\n\r\n";
            }
        };
        MultipartBody body = MultipartBody.newBuilder()
                .boundary("Boundary")
                .addPart(mutable)
                .build();

        String encoded;
        try (InputStream input = body.getInputStream()) {
            encoded = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, headerCalls.get());
        assertEquals(1, lengthCalls.get());
        assertTrue(encoded.contains("X-Part: original"));
        assertFalse(encoded.contains("X-Part: changed"));
        assertEquals(encoded.getBytes(StandardCharsets.UTF_8).length, body.getLength());
    }

    @Test
    void customPartStreamsMustMatchTheirDeclaredLength() {
        Part invalid = new Part() {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[] {1, 2});
            }

            @Override
            public long getLength() {
                return 1;
            }

            @Override
            public String getHeaders() {
                return "X-Part: value\r\n\r\n";
            }
        };
        MultipartBody body = MultipartBody.newBuilder().addPart(invalid).build();

        assertThrows(IOException.class, () -> {
            try (InputStream input = body.getInputStream()) {
                input.readAllBytes();
            }
        });
    }
}
