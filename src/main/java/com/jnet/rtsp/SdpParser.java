package com.jnet.rtsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Small line-oriented SDP parser for the session and media fields used by RTSP.
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class SdpParser {
    public static class SdpInfo {
        private final String version;
        private final String origin;
        private final String sessionName;
        private final String control;
        private final List<MediaDescription> mediaDescriptions;

        public SdpInfo(String version, String origin, String sessionName,
                       List<MediaDescription> mediaDescriptions) {
            this(version, origin, sessionName, null, mediaDescriptions);
        }

        private SdpInfo(String version, String origin, String sessionName, String control,
                        List<MediaDescription> mediaDescriptions) {
            this.version = version;
            this.origin = origin;
            this.sessionName = sessionName;
            this.control = control;
            this.mediaDescriptions = mediaDescriptions == null || mediaDescriptions.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(mediaDescriptions));
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

        /** Returns the session-level aggregate {@code a=control} value. */
        public String getControl() {
            return control;
        }

        public List<MediaDescription> getMediaDescriptions() {
            return mediaDescriptions;
        }

        public boolean hasMedia() {
            return !mediaDescriptions.isEmpty();
        }

        public List<MediaDescription> getMediaDescriptionsByType(String type) {
            if (type == null || mediaDescriptions.isEmpty()) {
                return Collections.emptyList();
            }
            List<MediaDescription> result = new ArrayList<>();
            for (MediaDescription description : mediaDescriptions) {
                if (description.getType().toString().equalsIgnoreCase(type)) {
                    result.add(description);
                }
            }
            return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
        }

        public MediaDescription getVideoDescription() {
            return firstByType(MediaType.VIDEO);
        }

        public MediaDescription getAudioDescription() {
            return firstByType(MediaType.AUDIO);
        }

        private MediaDescription firstByType(MediaType type) {
            for (MediaDescription description : mediaDescriptions) {
                if (description.getType() == type) {
                    return description;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return "SdpInfo{version='" + version + "', origin='" + origin
                    + "', mediaCount=" + mediaDescriptions.size() + '}';
        }
    }

    public enum MediaType {
        VIDEO,
        AUDIO,
        APPLICATION,
        DATA;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Description of one {@code m=} media section. */
    public static class MediaDescription {
        private final MediaType type;
        private final int port;
        private final String protocol;
        private final String format;
        private final String payloadType;
        private final String rtpMap;
        private final String fmtp;
        private final String control;

        public MediaDescription(MediaType type, int port, String protocol, String format,
                                String payloadType, String rtpMap, String control) {
            this(type, port, protocol, format, payloadType, rtpMap, null, control);
        }

        public MediaDescription(MediaType type, int port, String protocol, String format,
                                String payloadType, String rtpMap, String fmtp, String control) {
            if (type == null) {
                throw new IllegalArgumentException("Media type cannot be null");
            }
            this.type = type;
            this.port = port;
            this.protocol = protocol;
            this.format = format;
            this.payloadType = payloadType;
            this.rtpMap = rtpMap;
            this.fmtp = fmtp;
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

        /** Returns the complete format list from the {@code m=} line. */
        public String getFormat() {
            return format;
        }

        /** Returns the first advertised format/payload identifier. */
        public String getPayloadType() {
            return payloadType;
        }

        /** Returns the codec mapping for the first advertised payload, without its identifier. */
        public String getRtpMap() {
            return rtpMap;
        }

        /** Returns format parameters for the first advertised payload, without its identifier. */
        public String getFmtp() {
            return fmtp;
        }

        public String getControl() {
            return control;
        }
    }

    /** Parses SDP using a single pass over its lines. */
    public static SdpInfo parse(String sdpContent) {
        if (sdpContent == null || sdpContent.trim().isEmpty()) {
            throw new IllegalArgumentException("SDP content cannot be null or empty");
        }

        String version = "0";
        String origin = "-";
        String sessionName = "";
        String sessionControl = null;
        List<MediaDescription> mediaDescriptions = new ArrayList<>();
        MediaBuilder currentMedia = null;
        boolean mediaSectionStarted = false;

        String[] lines = sdpContent.split("\\r\\n|\\n|\\r", -1);
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.length() < 2 || line.charAt(1) != '=') {
                continue;
            }

            char field = line.charAt(0);
            String value = line.substring(2).trim();
            if (field == 'm') {
                mediaSectionStarted = true;
                if (currentMedia != null) {
                    mediaDescriptions.add(currentMedia.build());
                }
                currentMedia = MediaBuilder.parse(value);
                continue;
            }
            if (field == 'a') {
                if (currentMedia != null) {
                    currentMedia.acceptAttribute(value);
                } else if (!mediaSectionStarted && sessionControl == null) {
                    sessionControl = controlValue(value);
                }
                continue;
            }
            if (currentMedia != null) {
                continue;
            }

            switch (field) {
                case 'v':
                    if (!value.isEmpty()) {
                        version = value;
                    }
                    break;
                case 'o':
                    origin = value;
                    break;
                case 's':
                    sessionName = value;
                    break;
                default:
                    break;
            }
        }

        if (currentMedia != null) {
            mediaDescriptions.add(currentMedia.build());
        }
        return new SdpInfo(version, origin, sessionName, sessionControl, mediaDescriptions);
    }

    private static String controlValue(String attribute) {
        int colon = attribute.indexOf(':');
        if (colon < 0 || !"control".equalsIgnoreCase(attribute.substring(0, colon).trim())) {
            return null;
        }
        String value = attribute.substring(colon + 1).trim();
        return value.isEmpty() ? null : value;
    }

    private static final class MediaBuilder {
        private final MediaType type;
        private final int port;
        private final String protocol;
        private final String format;
        private final String payloadType;
        private String rtpMap;
        private String fmtp;
        private String control;

        private MediaBuilder(MediaType type, int port, String protocol,
                             String format, String payloadType) {
            this.type = type;
            this.port = port;
            this.protocol = protocol;
            this.format = format;
            this.payloadType = payloadType;
        }

        static MediaBuilder parse(String value) {
            String[] tokens = value.trim().split("\\s+");
            if (tokens.length < 4) {
                return null;
            }

            String portToken = tokens[1];
            int slash = portToken.indexOf('/');
            if (slash >= 0) {
                portToken = portToken.substring(0, slash);
            }

            final int port;
            try {
                port = Integer.parseInt(portToken);
            } catch (NumberFormatException e) {
                return null;
            }
            if (port < 0 || port > 65_535) {
                return null;
            }

            MediaType type;
            try {
                type = MediaType.valueOf(tokens[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                type = MediaType.APPLICATION;
            }

            StringBuilder formats = new StringBuilder(tokens[3]);
            for (int i = 4; i < tokens.length; i++) {
                formats.append(' ').append(tokens[i]);
            }
            return new MediaBuilder(type, port, tokens[2], formats.toString(), tokens[3]);
        }

        void acceptAttribute(String attribute) {
            int colon = attribute.indexOf(':');
            String name = colon >= 0 ? attribute.substring(0, colon) : attribute;
            String value = colon >= 0 ? attribute.substring(colon + 1).trim() : "";

            if ("control".equalsIgnoreCase(name)) {
                if (control == null) {
                    control = value;
                }
                return;
            }
            if (!"rtpmap".equalsIgnoreCase(name) && !"fmtp".equalsIgnoreCase(name)) {
                return;
            }

            int separator = firstWhitespace(value);
            if (separator < 0 || !payloadType.equals(value.substring(0, separator))) {
                return;
            }
            String details = value.substring(separator).trim();
            if (details.isEmpty()) {
                return;
            }
            if ("rtpmap".equalsIgnoreCase(name) && rtpMap == null) {
                rtpMap = details;
            } else if ("fmtp".equalsIgnoreCase(name) && fmtp == null) {
                fmtp = details;
            }
        }

        MediaDescription build() {
            return new MediaDescription(type, port, protocol, format,
                    payloadType, rtpMap, fmtp, control);
        }

        private static int firstWhitespace(String value) {
            for (int i = 0; i < value.length(); i++) {
                if (Character.isWhitespace(value.charAt(i))) {
                    return i;
                }
            }
            return -1;
        }
    }
}
