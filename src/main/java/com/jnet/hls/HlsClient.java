package com.jnet.hls;

import com.jnet.core.JNet;
import com.jnet.protocol.ProtocolAdapter;
import com.jnet.protocol.ProtocolRequest;
import com.jnet.protocol.ProtocolResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * HLS (HTTP Live Streaming) Client
 * Supports M3U8 playlist parsing and segment downloading
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class HlsClient implements ProtocolAdapter {

    private final String url;
    private final Duration readTimeout;
    private final int refreshInterval;

    private HlsClient(Builder builder) {
        this.url = builder.url;
        this.readTimeout = builder.readTimeout;
        this.refreshInterval = builder.refreshInterval;
    }

    // ========== Factory Methods ==========

    public static HlsClient fromUrl(String url) {
        return newBuilder().url(url).build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Adapter Implementation ==========

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public ProtocolResponse execute(ProtocolRequest request) throws IOException {
        String playlistContent = JNet.get(url);

        if (playlistContent == null || playlistContent.isEmpty()) {
            throw new IOException("Empty playlist content");
        }

        M3U8Parser.HlsMediaPlaylist playlist = M3U8Parser.parse(playlistContent);

        return ProtocolResponse.success()
                .host(extractHost(url), extractPort(url))
                .data(playlistContent.getBytes())
                .header("Content-Type", "application/vnd.apple.mpegurl")
                .build();
    }

    @Override
    public java.util.concurrent.CompletableFuture<ProtocolResponse> executeAsync(ProtocolRequest request) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return execute(request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ========== HLS Operations ==========

    public M3U8Parser.HlsMediaPlaylist getPlaylist() throws IOException {
        String content = JNet.get(url);
        return M3U8Parser.parse(content);
    }

    public void downloadSegments(String outputDir, ProgressListener listener) throws IOException {
        downloadSegments(outputDir, null, listener);
    }

    public void downloadSegments(String outputDir, String mediaType, ProgressListener listener) throws IOException {
        M3U8Parser.HlsMediaPlaylist playlist = getPlaylist();
        if (!playlist.isMedia()) {
            throw new IOException("Cannot download non-media playlist");
        }

        java.util.List<M3U8Parser.HlsSegment> segments = playlist.getSegments();
        for (int i = 0; i < segments.size(); i++) {
            M3U8Parser.HlsSegment segment = segments.get(i);

            String segmentUrl = segment.getUri();
            if (segmentUrl == null || segmentUrl.isEmpty()) {
                throw new IOException("Invalid segment URL: " + segmentUrl);
            }

            if (!segmentUrl.startsWith("http")) {
                segmentUrl = resolveUrl(url, segmentUrl);
            }

            String fileContent = JNet.get(segmentUrl);
            byte[] data = fileContent.getBytes();

            if (listener != null) {
                listener.onUpdate(i + 1, segments.size(), data.length);
            }

            String filename = extractFilename(segmentUrl, i);
            Files.write(Paths.get(outputDir, filename), data);
        }

        if (listener != null) {
            listener.onComplete();
        }
    }

    private String extractFilename(String url, int index) {
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (filename.contains("?")) {
            filename = filename.substring(0, filename.indexOf("?"));
        }
        if (filename.contains("#")) {
            filename = filename.substring(0, filename.indexOf("#"));
        }
        if (!filename.contains(".")) {
            filename = "segment_" + index + ".ts";
        }
        return filename;
    }

    private String resolveUrl(String baseUrl, String relativeUrl) {
        if (relativeUrl.startsWith("/")) {
            String baseHost = extractHost(baseUrl);
            int basePort = extractPort(baseUrl);
            return "http://" + baseHost + ":" + basePort + relativeUrl;
        }
        return baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + relativeUrl;
    }

    private String extractHost(String url) {
        try {
            if (url.startsWith("http://")) {
                url = url.substring(7);
            } else if (url.startsWith("https://")) {
                url = url.substring(8);
            }
            return url.substring(0, url.indexOf('/'));
        } catch (Exception e) {
            return "";
        }
    }

    private int extractPort(String url) {
        try {
            String hostPart = url;
            if (url.startsWith("http://")) {
                hostPart = url.substring(7);
            } else if (url.startsWith("https://")) {
                hostPart = url.substring(8);
            }
            int slashIndex = hostPart.indexOf('/');
            if (slashIndex > 0) {
                hostPart = hostPart.substring(0, slashIndex);
            }
            int colonIndex = hostPart.indexOf(':');
            if (colonIndex > 0) {
                return Integer.parseInt(hostPart.substring(colonIndex + 1));
            }
            return url.startsWith("https") ? 443 : 80;
        } catch (Exception e) {
            return 80;
        }
    }

    // ========== Builder ==========

    public static class Builder {
        private String url;
        private Duration readTimeout = Duration.ofSeconds(30);
        private int refreshInterval = 3;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        public Builder refreshInterval(int seconds) {
            this.refreshInterval = seconds;
            return this;
        }

        public HlsClient build() {
            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("URL must be set");
            }
            return new HlsClient(this);
        }
    }

    public interface ProgressListener {
        void onUpdate(int segment, int total, long bytes);
        void onComplete();
    }
}
