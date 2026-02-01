package com.jnet.download;

import com.jnet.core.JNetClient;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

/**
 * 文件下载工具类
 * 支持流式下载和进度回调
 */
public class Download {

    /**
     * 下载到文件
     */
    public static void toFile(String url, File destination, ProgressListener listener) throws IOException, InterruptedException {
        HttpClient client = JNetClient.getInstance().getHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        checkResponse(response);

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);

        try (InputStream in = response.body();
             FileOutputStream out = new FileOutputStream(destination)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                if (listener != null) {
                    listener.update(totalRead, contentLength, false);
                }
            }

            if (listener != null) {
                listener.update(totalRead, contentLength, true);
            }
        }
    }

    /**
     * 下载到字节数组
     */
    public static byte[] toBytes(String url, ProgressListener listener) throws IOException, InterruptedException {
        HttpClient client = JNetClient.getInstance().getHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        checkResponse(response);

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        ByteArrayOutputStream out = new ByteArrayOutputStream(contentLength > 0 ? (int)contentLength : 8192);

        try (InputStream in = response.body()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                if (listener != null) {
                    listener.update(totalRead, contentLength, false);
                }
            }

            if (listener != null) {
                listener.update(totalRead, contentLength, true);
            }
        }

        return out.toByteArray();
    }

    /**
     * 异步下载到文件
     */
    public static CompletableFuture<Void> toFileAsync(String url, File destination, ProgressListener listener) {
        return CompletableFuture.runAsync(() -> {
            try {
                toFile(url, destination, listener);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void checkResponse(HttpResponse<?> response) throws IOException {
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Download failed with HTTP " + code);
        }
    }
}
