package com.jnet.rtsp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SDP (Session Description Protocol) Parser
 * Parses SDP format as defined in RFC 4566
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class SdpParser {

    // ========== Data Models ==========

    /**
     * SDP Information
     */
    public static class SdpInfo {
        private final String version;
        private final String origin;
        private final String sessionName;
        private final List<MediaDescription> mediaDescriptions;

        public SdpInfo(String version, String origin, String sessionName,
                      List<MediaDescription> mediaDescriptions) {
            this.version = version;
            this.origin = origin;
            this.sessionName = sessionName;
            this.mediaDescriptions = mediaDescriptions;
        }

        public String getVersion() {
            return version;
        }

        public String getOrigin() {
            return origin;
        }

        public String getSessionName() {
            return sessionName;
        }

        public List<MediaDescription> getMediaDescriptions() {
            return mediaDescriptions;
        }

        public boolean hasMedia() {
            return mediaDescriptions != null && !mediaDescriptions.isEmpty();
        }

        public List<MediaDescription> getMediaDescriptionsByType(String type) {
            List<MediaDescription> result = new ArrayList<>();
            for (MediaDescription desc : mediaDescriptions) {
                if (desc.getType().toString().equalsIgnoreCase(type)) {
                    result.add(desc);
                }
            }
            return result;
        }

        public MediaDescription getVideoDescription() {
            List<MediaDescription> videos = getMediaDescriptionsByType("video");
            return videos.isEmpty() ? null : videos.get(0);
        }

        public MediaDescription getAudioDescription() {
            List<MediaDescription> audios = getMediaDescriptionsByType("audio");
            return audios.isEmpty() ? null : audios.get(0);
        }

        @Override
        public String toString() {
            return String.format(
                    "SdpInfo{version='%s', origin='%s', mediaCount=%d}",
                    version, origin, mediaDescriptions.size());
        }
    }

    /**
     * Media Type
     */
    public enum MediaType {
        VIDEO,
        AUDIO,
        APPLICATION,
        DATA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * Media Description
     * Represents m= line for a media track
     */
    public static class MediaDescription {
        private final MediaType type;
        private final int port;
        private final String protocol;
        private final String format;
        private final String payloadType;
        private final String rtpMap;
        private final String control;

        public MediaDescription(MediaType type, int port, String protocol, String format,
                           String payloadType, String rtpMap, String control) {
            this.type = type;
            this.port = port;
            this.protocol = protocol;
            this.format = format;
            this.payloadType = payloadType;
            this.rtpMap = rtpMap;
            this.control = control;
        }

        public MediaType getType() {
            return type;
        }

        public int getPort() {
            return port;
        }

        public String getProtocol() {
            return protocol;
        }

        public String getFormat() {
            return format;
        }

        public String getPayloadType() {
            return payloadType;
        }

        public String getRtpMap() {
            return rtpMap;
        }

        public String getControl() {
            return control;
        }
    }

    // ========== Parser Methods ==========

    private static final Pattern VERSION_PATTERN = Pattern.compile("v=([0-9]+)");
    private static final Pattern SESSION_NAME_PATTERN = Pattern.compile("s=([^\r\n]+)");
    private static final Pattern MEDIA_PATTERN = Pattern.compile("m=(\\w+)\\s+(\\d+)\\s+(\\S+)\\s+(.*)");
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("a=([^:]+):?(.*)");

    /**
     * Parse SDP content string
     */
    public static SdpInfo parse(String sdpContent) {
        if (sdpContent == null || sdpContent.trim().isEmpty()) {
            throw new IllegalArgumentException("SDP content cannot be null or empty");
        }

        // Parse version
        String version = "0";
        Matcher versionMatcher = VERSION_PATTERN.matcher(sdpContent);
        if (versionMatcher.find()) {
            version = versionMatcher.group(1);
        }

        // Parse session name
        String sessionName = "";
        Matcher sessionNameMatcher = SESSION_NAME_PATTERN.matcher(sdpContent);
        if (sessionNameMatcher.find()) {
            sessionName = sessionNameMatcher.group(1);
        }

        // Parse origin (simplified)
        String origin = "-";
        if (sdpContent.contains("o=")) {
            int oIndex = sdpContent.indexOf("o=");
            int newlineIndex = sdpContent.indexOf("\n", oIndex);
            if (newlineIndex > oIndex) {
                origin = sdpContent.substring(oIndex, newlineIndex).trim();
            }
        }

        // Parse media descriptions
        List<MediaDescription> mediaDescriptions = new ArrayList<>();
        String[] sections = sdpContent.split("(?=m=)");

        for (String section : sections) {
            Matcher mediaMatcher = MEDIA_PATTERN.matcher(section);
            if (!mediaMatcher.find()) {
                continue;
            }

            String typeStr = mediaMatcher.group(1);
            int port = Integer.parseInt(mediaMatcher.group(2));
            String protocol = mediaMatcher.group(3);
            String formats = mediaMatcher.group(4);

            MediaType type;
            try {
                type = MediaType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = MediaType.APPLICATION;
            }

            // Parse attributes from this section
            String payloadType = extractAttribute(section, "rtpmap");
            String control = extractAttribute(section, "control");
            String fmtp = extractAttribute(section, "fmtp");

            mediaDescriptions.add(new MediaDescription(type, port, protocol, formats, payloadType, fmtp, control));
        }

        return new SdpInfo(version, origin, sessionName, mediaDescriptions);
    }

    /**
     * Extract attribute value from section
     */
    private static String extractAttribute(String section, String attributeName) {
        Pattern pattern = Pattern.compile("a=" + attributeName + ":(.*)");
        Matcher matcher = pattern.matcher(section);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
