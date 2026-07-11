package com.jnet.hls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Parses HLS media playlists without external dependencies. */
public final class M3U8Parser {

    private static final String MASTER_PLAYLIST_ERROR =
            "Master playlists are not supported; provide a media playlist URL";

    /** @deprecated This parser is stateless; use {@link #parse(String)}. */
    @Deprecated
    public M3U8Parser() {
    }

    /**
     * Parses one media playlist. Master playlists are rejected explicitly so a
     * caller never mistakes a variant URI for a media segment.
     */
    public static HlsMediaPlaylist parse(String content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Playlist content cannot be null or empty");
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            boolean headerSeen = false;
            boolean endList = false;
            String targetDuration = null;
            Integer mediaSequence = 0;
            boolean mediaSequenceSeen = false;
            Double pendingDuration = null;
            String pendingInfo = null;
            ParsedByteRange pendingByteRange = null;
            HlsByteRange previousByteRange = null;
            String previousByteRangeUri = null;
            HlsInitializationSegment initializationSegment = null;
            HlsByteRange previousMapByteRange = null;
            String previousMapUri = null;
            HlsEncryptionKey encryptionKey = null;
            List<HlsSegment> segments = new ArrayList<>();

            String rawLine;
            int lineNumber = 0;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && !rawLine.isEmpty() && rawLine.charAt(0) == '\uFEFF') {
                    rawLine = rawLine.substring(1);
                }
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (!headerSeen) {
                    if (!"#EXTM3U".equals(line)) {
                        throw parseError(lineNumber, "playlist must start with #EXTM3U");
                    }
                    headerSeen = true;
                    continue;
                }

                if (isMasterTag(line)) {
                    throw parseError(lineNumber, MASTER_PLAYLIST_ERROR);
                }
                if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                    if (targetDuration != null) {
                        throw parseError(lineNumber, "duplicate EXT-X-TARGETDURATION");
                    }
                    long value = parseUnsignedDecimal(valueAfterColon(line, lineNumber), lineNumber,
                            "target duration");
                    if (value == 0) {
                        throw parseError(lineNumber, "target duration must be positive");
                    }
                    targetDuration = Long.toString(value);
                    continue;
                }
                if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                    if (mediaSequenceSeen || !segments.isEmpty()) {
                        throw parseError(lineNumber, "invalid EXT-X-MEDIA-SEQUENCE position");
                    }
                    long value = parseUnsignedDecimal(valueAfterColon(line, lineNumber), lineNumber,
                            "media sequence");
                    if (value > Integer.MAX_VALUE) {
                        throw parseError(lineNumber, "media sequence exceeds the supported integer range");
                    }
                    mediaSequence = (int) value;
                    mediaSequenceSeen = true;
                    continue;
                }
                if ("#EXT-X-ENDLIST".equals(line)) {
                    endList = true;
                    continue;
                }
                if (line.startsWith("#EXT-X-MAP:")) {
                    Map<String, String> attributes = parseAttributes(valueAfterColon(line, lineNumber), lineNumber);
                    String uri = requiredAttribute(attributes, "URI", lineNumber, "EXT-X-MAP");
                    HlsByteRange byteRange = null;
                    String range = attributes.get("BYTERANGE");
                    if (range != null) {
                        ParsedByteRange parsed = parseByteRange(range, lineNumber);
                        byteRange = resolveByteRange(parsed, uri, previousMapByteRange, previousMapUri,
                                lineNumber, "map");
                        previousMapByteRange = byteRange;
                        previousMapUri = uri;
                    } else {
                        previousMapByteRange = null;
                        previousMapUri = null;
                    }
                    if (encryptionKey != null && encryptionKey.getIv() == null) {
                        throw parseError(lineNumber,
                                "AES-128 encrypted EXT-X-MAP requires an explicit IV");
                    }
                    initializationSegment = new HlsInitializationSegment(uri, byteRange, encryptionKey);
                    continue;
                }
                if (line.startsWith("#EXT-X-KEY:")) {
                    encryptionKey = parseEncryptionKey(valueAfterColon(line, lineNumber), lineNumber);
                    continue;
                }
                if (line.startsWith("#EXT-X-BYTERANGE:")) {
                    if (pendingByteRange != null) {
                        throw parseError(lineNumber, "duplicate byte range before a media segment");
                    }
                    pendingByteRange = parseByteRange(valueAfterColon(line, lineNumber), lineNumber);
                    continue;
                }
                if (line.startsWith("#EXTINF:")) {
                    if (pendingDuration != null) {
                        throw parseError(lineNumber, "EXTINF is missing its media URI");
                    }
                    String value = valueAfterColon(line, lineNumber);
                    int comma = value.indexOf(',');
                    String durationText = comma >= 0 ? value.substring(0, comma).trim() : value.trim();
                    pendingDuration = parseDuration(durationText, lineNumber);
                    pendingInfo = comma >= 0 ? value.substring(comma + 1) : null;
                    continue;
                }
                if (line.charAt(0) == '#') {
                    continue;
                }
                if (pendingDuration == null) {
                    throw parseError(lineNumber, "media URI is missing EXTINF");
                }

                HlsByteRange byteRange = null;
                if (pendingByteRange != null) {
                    byteRange = resolveByteRange(pendingByteRange, line, previousByteRange,
                            previousByteRangeUri, lineNumber, "media segment");
                    previousByteRange = byteRange;
                    previousByteRangeUri = line;
                } else {
                    previousByteRange = null;
                    previousByteRangeUri = null;
                }
                long sequenceNumber = Math.addExact(mediaSequence.longValue(), segments.size());
                segments.add(new HlsSegment(line, pendingDuration, pendingInfo, byteRange,
                        initializationSegment, encryptionKey, sequenceNumber));
                pendingDuration = null;
                pendingInfo = null;
                pendingByteRange = null;
            }

            if (!headerSeen) {
                throw new IllegalArgumentException("Playlist must start with #EXTM3U");
            }
            if (targetDuration == null) {
                throw new IllegalArgumentException("Media playlist is missing EXT-X-TARGETDURATION");
            }
            if (pendingDuration != null || pendingByteRange != null) {
                throw new IllegalArgumentException("Playlist ends before the pending media segment URI");
            }
            return new HlsMediaPlaylist(targetDuration, mediaSequence, segments, endList);
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to read in-memory playlist", impossible);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Playlist numeric value overflow", overflow);
        }
    }

    private static boolean isMasterTag(String line) {
        return line.startsWith("#EXT-X-STREAM-INF:")
                || line.startsWith("#EXT-X-I-FRAME-STREAM-INF:")
                || line.startsWith("#EXT-X-MEDIA:");
    }

    private static HlsEncryptionKey parseEncryptionKey(String value, int lineNumber) {
        Map<String, String> attributes = parseAttributes(value, lineNumber);
        String method = requiredAttribute(attributes, "METHOD", lineNumber, "EXT-X-KEY")
                .toUpperCase(Locale.ROOT);
        if ("NONE".equals(method)) {
            if (attributes.size() != 1) {
                throw parseError(lineNumber, "METHOD=NONE must not contain other key attributes");
            }
            return null;
        }
        if (!"AES-128".equals(method)) {
            throw parseError(lineNumber, "Unsupported encryption method: " + method);
        }
        String keyFormat = attributes.get("KEYFORMAT");
        if (keyFormat != null && !"identity".equals(keyFormat)) {
            throw parseError(lineNumber, "Unsupported key format: " + keyFormat);
        }
        String uri = requiredAttribute(attributes, "URI", lineNumber, "EXT-X-KEY");
        byte[] iv = attributes.containsKey("IV") ? parseIv(attributes.get("IV"), lineNumber) : null;
        return new HlsEncryptionKey(method, uri, iv);
    }

    private static byte[] parseIv(String value, int lineNumber) {
        if (!(value.startsWith("0x") || value.startsWith("0X"))) {
            throw parseError(lineNumber, "AES-128 IV must start with 0x");
        }
        String hex = value.substring(2);
        if (hex.isEmpty() || hex.length() > 32) {
            throw parseError(lineNumber, "AES-128 IV must contain at most 128 bits");
        }
        byte[] iv = new byte[16];
        int destination = iv.length * 2 - hex.length();
        for (int i = 0; i < hex.length(); i++) {
            int digit = Character.digit(hex.charAt(i), 16);
            if (digit < 0) {
                throw parseError(lineNumber, "AES-128 IV contains non-hexadecimal characters");
            }
            int nibble = destination + i;
            iv[nibble / 2] |= (byte) (digit << (nibble % 2 == 0 ? 4 : 0));
        }
        return iv;
    }

    private static Double parseDuration(String value, int lineNumber) {
        try {
            double duration = Double.parseDouble(value);
            if (!Double.isFinite(duration) || duration < 0) {
                throw parseError(lineNumber, "segment duration must be finite and non-negative");
            }
            return duration;
        } catch (NumberFormatException error) {
            throw parseError(lineNumber, "invalid segment duration: " + value);
        }
    }

    private static ParsedByteRange parseByteRange(String value, int lineNumber) {
        int separator = value.indexOf('@');
        if (separator != value.lastIndexOf('@')) {
            throw parseError(lineNumber, "invalid byte range: " + value);
        }
        String lengthText = separator < 0 ? value.trim() : value.substring(0, separator).trim();
        long length = parseUnsignedDecimal(lengthText, lineNumber, "byte range length");
        if (length == 0) {
            throw parseError(lineNumber, "byte range length must be positive");
        }
        Long offset = separator < 0 ? null
                : parseUnsignedDecimal(value.substring(separator + 1).trim(), lineNumber,
                        "byte range offset");
        return new ParsedByteRange(length, offset);
    }

    private static HlsByteRange resolveByteRange(ParsedByteRange parsed, String uri,
            HlsByteRange previous, String previousUri, int lineNumber, String kind) {
        long offset;
        if (parsed.offset != null) {
            offset = parsed.offset;
        } else {
            if (previous == null || !uri.equals(previousUri)) {
                throw parseError(lineNumber,
                        kind + " has an implicit byte range without a previous range for the same URI");
            }
            offset = Math.addExact(previous.offset, previous.length);
        }
        Math.addExact(offset, parsed.length - 1);
        return new HlsByteRange(parsed.length, offset);
    }

    private static long parseUnsignedDecimal(String value, int lineNumber, String name) {
        if (value.isEmpty()) {
            throw parseError(lineNumber, name + " is empty");
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                throw parseError(lineNumber, "invalid " + name + ": " + value);
            }
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw parseError(lineNumber, name + " exceeds the supported range");
        }
    }

    private static Map<String, String> parseAttributes(String value, int lineNumber) {
        Map<String, String> attributes = new LinkedHashMap<>();
        int cursor = 0;
        while (cursor < value.length()) {
            while (cursor < value.length()
                    && (value.charAt(cursor) == ',' || Character.isWhitespace(value.charAt(cursor)))) {
                cursor++;
            }
            if (cursor == value.length()) {
                break;
            }
            int equals = value.indexOf('=', cursor);
            if (equals < 0) {
                throw parseError(lineNumber, "invalid attribute list");
            }
            String name = value.substring(cursor, equals).trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                throw parseError(lineNumber, "empty attribute name");
            }
            cursor = equals + 1;
            String attributeValue;
            if (cursor < value.length() && value.charAt(cursor) == '"') {
                int closingQuote = value.indexOf('"', cursor + 1);
                if (closingQuote < 0) {
                    throw parseError(lineNumber, "unterminated quoted attribute: " + name);
                }
                attributeValue = value.substring(cursor + 1, closingQuote);
                cursor = closingQuote + 1;
                while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
                    cursor++;
                }
                if (cursor < value.length() && value.charAt(cursor) != ',') {
                    throw parseError(lineNumber, "invalid characters after attribute: " + name);
                }
            } else {
                int comma = value.indexOf(',', cursor);
                int end = comma < 0 ? value.length() : comma;
                attributeValue = value.substring(cursor, end).trim();
                cursor = end;
            }
            if (attributeValue.isEmpty()) {
                throw parseError(lineNumber, "empty attribute value: " + name);
            }
            if (attributes.put(name, attributeValue) != null) {
                throw parseError(lineNumber, "duplicate attribute: " + name);
            }
        }
        return attributes;
    }

    private static String requiredAttribute(Map<String, String> attributes, String name,
            int lineNumber, String tag) {
        String value = attributes.get(name);
        if (value == null || value.isEmpty()) {
            throw parseError(lineNumber, tag + " requires " + name);
        }
        return value;
    }

    private static String valueAfterColon(String line, int lineNumber) {
        int colon = line.indexOf(':');
        if (colon < 0 || colon == line.length() - 1) {
            throw parseError(lineNumber, "tag is missing a value");
        }
        return line.substring(colon + 1).trim();
    }

    private static IllegalArgumentException parseError(int lineNumber, String message) {
        return new IllegalArgumentException("Invalid HLS playlist at line " + lineNumber + ": " + message);
    }

    private static final class ParsedByteRange {
        private final long length;
        private final Long offset;

        private ParsedByteRange(long length, Long offset) {
            this.length = length;
            this.offset = offset;
        }
    }

    /** Immutable byte range using an absolute offset. */
    public static final class HlsByteRange {
        private final long length;
        private final long offset;

        public HlsByteRange(long length, long offset) {
            if (length <= 0 || offset < 0) {
                throw new IllegalArgumentException("Byte range requires a positive length and non-negative offset");
            }
            try {
                Math.addExact(offset, length - 1);
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("Byte range exceeds the supported range", error);
            }
            this.length = length;
            this.offset = offset;
        }

        public long getLength() {
            return length;
        }

        public long getOffset() {
            return offset;
        }

    }

    /** Immutable AES-128 key descriptor. The key bytes are fetched by the client. */
    public static final class HlsEncryptionKey {
        private final String method;
        private final String uri;
        private final byte[] iv;

        public HlsEncryptionKey(String method, String uri, byte[] iv) {
            if (!"AES-128".equals(method)) {
                throw new IllegalArgumentException("Only AES-128 keys are supported");
            }
            if (uri == null || uri.isEmpty()) {
                throw new IllegalArgumentException("Encryption key URI must not be empty");
            }
            if (iv != null && iv.length != 16) {
                throw new IllegalArgumentException("AES-128 IV must contain 16 bytes");
            }
            this.method = method;
            this.uri = uri;
            this.iv = iv == null ? null : iv.clone();
        }

        public String getMethod() {
            return method;
        }

        public String getUri() {
            return uri;
        }

        public byte[] getIv() {
            return iv == null ? null : iv.clone();
        }

    }

    /** Immutable media initialization section declared by EXT-X-MAP. */
    public static final class HlsInitializationSegment {
        private final String uri;
        private final HlsByteRange byteRange;
        private final HlsEncryptionKey encryptionKey;

        public HlsInitializationSegment(String uri, HlsByteRange byteRange,
                HlsEncryptionKey encryptionKey) {
            if (uri == null || uri.isEmpty()) {
                throw new IllegalArgumentException("Initialization segment URI must not be empty");
            }
            this.uri = uri;
            this.byteRange = byteRange;
            this.encryptionKey = encryptionKey;
        }

        public String getUri() {
            return uri;
        }

        public HlsByteRange getByteRange() {
            return byteRange;
        }

        public HlsEncryptionKey getEncryptionKey() {
            return encryptionKey;
        }

    }

    /** Immutable media segment descriptor. */
    public static class HlsSegment {
        private static final byte[] NO_DATA = new byte[0];

        private final String uri;
        private final Double duration;
        private final String info;
        private final HlsByteRange byteRange;
        private final HlsInitializationSegment initializationSegment;
        private final HlsEncryptionKey encryptionKey;
        private final long sequenceNumber;

        public HlsSegment(String uri, Double duration, String info) {
            this(uri, duration, info, null, null, null, 0);
        }

        private HlsSegment(String uri, Double duration, String info, HlsByteRange byteRange,
                HlsInitializationSegment initializationSegment, HlsEncryptionKey encryptionKey,
                long sequenceNumber) {
            this.uri = Objects.requireNonNull(uri, "uri");
            this.duration = duration;
            this.info = info;
            this.byteRange = byteRange;
            this.initializationSegment = initializationSegment;
            this.encryptionKey = encryptionKey;
            this.sequenceNumber = sequenceNumber;
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

        public HlsByteRange getByteRange() {
            return byteRange;
        }

        public HlsInitializationSegment getInitializationSegment() {
            return initializationSegment;
        }

        public HlsEncryptionKey getEncryptionKey() {
            return encryptionKey;
        }

        public long getSequenceNumber() {
            return sequenceNumber;
        }

        /**
         * Returns the legacy empty payload placeholder. Parsed playlists contain
         * descriptors, not downloaded segment data.
         *
         * @deprecated Download segments with {@link HlsClient#downloadSegments(String,
         *             HlsClient.ProgressListener)}.
         */
        @Deprecated
        public byte[] getData() {
            return NO_DATA;
        }
    }

    /** Immutable HLS media playlist. */
    public static class HlsMediaPlaylist {
        private final String targetDuration;
        private final Integer mediaSequence;
        private final List<HlsSegment> segments;
        private final boolean endList;
        private final Map<String, String> metadata;

        public HlsMediaPlaylist(String targetDuration, Integer mediaSequence,
                List<HlsSegment> segments, boolean endList) {
            this.targetDuration = Objects.requireNonNull(targetDuration, "targetDuration");
            this.mediaSequence = Objects.requireNonNull(mediaSequence, "mediaSequence");
            this.segments = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(segments, "segments")));
            this.endList = endList;
            Map<String, String> values = new LinkedHashMap<>();
            values.put("targetDuration", targetDuration);
            values.put("mediaSequence", String.valueOf(mediaSequence));
            this.metadata = Collections.unmodifiableMap(values);
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
            for (HlsSegment segment : segments) {
                if (segment.getDuration() != null) {
                    total += segment.getDuration();
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
            return metadata;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "HlsMediaPlaylist{segments=%d, duration=%.2fs, live=%s}",
                    segments.size(), getDuration(), isLive());
        }
    }
}
