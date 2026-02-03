package com.jnet.hls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M3U8 Playlist Parser
 * Parses HLS (HTTP Live Streaming) playlist format
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class M3U8Parser {

    // ========== Regex Patterns ==========

    private static final Pattern EXT_X_VERSION_TAG = Pattern.compile("#EXT-X-VERSION:(\\d+)");
    private static final Pattern EXT_X_TARGET_DURATION = Pattern.compile("#EXT-X-TARGETDURATION:(\\d+(?:\\.\\d+)?)");
    private static final Pattern EXT_X_MEDIA_SEQUENCE = Pattern.compile("#EXT-X-MEDIA-SEQUENCE:(\\d+)");
    private static final Pattern EXT_X_ENDLIST = Pattern.compile("#EXT-X-ENDLIST");
    private static final Pattern EXT_X_STREAM_INF = Pattern.compile("#EXT-X-STREAM-INF:(.*)");
    private static final Pattern EXTINF_TAG = Pattern.compile("#EXTINF:(\\d+(?:\\.\\d+)?)(?:,(.*))?");
    private static final Pattern EXT_X_BYTERANGE = Pattern.compile("#EXT-X-BYTERANGE:(\\d+)@?(\\d+)?");

    // ========== Parser Methods ==========

    /**
     * Parse M3U8 playlist from content string
     */
    public static HlsMediaPlaylist parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Playlist content cannot be null or empty");
        }

        // Check if it's a master playlist (contains EXT-X-STREAM-INF)
        if (EXT_X_STREAM_INF.matcher(content).find()) {
            throw new IllegalArgumentException("Master playlists not supported in basic parser");
        }

        String targetDuration = parseTargetDuration(content);
        Integer mediaSequence = parseMediaSequence(content);
        boolean isEndList = EXT_X_ENDLIST.matcher(content).find();

        List<HlsSegment> segments = parseSegments(content);

        return new HlsMediaPlaylist(targetDuration, mediaSequence, segments, isEndList);
    }

    /**
     * Parse target duration
     */
    private static String parseTargetDuration(String content) {
        Matcher matcher = EXT_X_TARGET_DURATION.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "10";
    }

    /**
     * Parse media sequence number
     */
    private static Integer parseMediaSequence(String content) {
        Matcher matcher = EXT_X_MEDIA_SEQUENCE.matcher(content);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    /**
     * Parse segments from playlist
     */
    private static List<HlsSegment> parseSegments(String content) {
        List<HlsSegment> segments = new ArrayList<>();

        String[] lines = content.split("\\r?\\n");
        Double currentDuration = null;
        String currentInfo = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.isEmpty() || line.startsWith("#EXT")) {
                // Parse EXTINF tag
                Matcher extinfMatcher = EXTINF_TAG.matcher(line);
                if (extinfMatcher.find()) {
                    currentDuration = Double.parseDouble(extinfMatcher.group(1));
                    currentInfo = extinfMatcher.group(2);
                }
                continue;
            }

            // If it's a URL and we have duration, create a segment
            if (!line.startsWith("#") && currentDuration != null) {
                segments.add(new HlsSegment(line, currentDuration, currentInfo));
                currentDuration = null;
                currentInfo = null;
            }
        }

        return segments;
    }

    // ========== Data Models ==========

    /**
     * HLS Segment
     */
    public static class HlsSegment {
        private final String uri;
        private final Double duration;
        private final String info;

        public HlsSegment(String uri, Double duration, String info) {
            this.uri = uri;
            this.duration = duration;
            this.info = info;
        }

        public String getUri() {
            return uri;
        }

        public Double getDuration() {
            return duration;
        }

        public String getInfo() {
            return info;
        }

        public byte[] getData() {
            return new byte[0]; // Placeholder for actual segment data
        }
    }

    /**
     * HLS Media Playlist
     */
    public static class HlsMediaPlaylist {
        private final String targetDuration;
        private final Integer mediaSequence;
        private final List<HlsSegment> segments;
        private final boolean endList;

        public HlsMediaPlaylist(String targetDuration, Integer mediaSequence,
                              List<HlsSegment> segments, boolean endList) {
            this.targetDuration = targetDuration;
            this.mediaSequence = mediaSequence;
            this.segments = segments;
            this.endList = endList;
        }

        public String getTargetDuration() {
            return targetDuration;
        }

        public Integer getMediaSequence() {
            return mediaSequence;
        }

        public List<HlsSegment> getSegments() {
            return segments;
        }

        public boolean isMaster() {
            return false;
        }

        public boolean isMedia() {
            return true;
        }

        public boolean isLive() {
            return !endList;
        }

        public boolean isEndList() {
            return endList;
        }

        public double getDuration() {
            double total = 0;
            for (HlsSegment seg : segments) {
                if (seg.getDuration() != null) {
                    total += seg.getDuration();
                }
            }
            return total;
        }

        public int getSegmentCount() {
            return segments.size();
        }

        public HlsSegment getSegment(int index) {
            return segments.get(index);
        }

        public Map<String, String> getMetadata() {
            Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("targetDuration", targetDuration);
            metadata.put("mediaSequence", String.valueOf(mediaSequence));
            return metadata;
        }

        @Override
        public String toString() {
            return String.format("HlsMediaPlaylist{segments=%d, duration=%.2fs, live=%s}",
                    segments.size(), getDuration(), isLive());
        }
    }
}
