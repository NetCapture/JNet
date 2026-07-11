package com.jnet.rtsp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestSdpParser {

    @Test
    void parsesMediaAttributesForTheFirstAdvertisedPayload() {
        String sdp = "v=0\r\n"
                + "o=- 2890844526 2890842807 IN IP4 127.0.0.1\r\n"
                + "s=Example stream\r\n"
                + "t=0 0\r\n"
                + "a=control:*\r\n"
                + "m=video 0 RTP/AVP 96 97\r\n"
                + "a=rtpmap:97 VP8/90000\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=fmtp:96 packetization-mode=1;profile-level-id=42e01f\r\n"
                + "a=control:trackID=0\r\n"
                + "m=audio 49170/2 RTP/AVP 0\r\n"
                + "a=control:trackID=1\r\n";

        SdpParser.SdpInfo info = SdpParser.parse(sdp);
        assertEquals("0", info.getVersion());
        assertEquals("- 2890844526 2890842807 IN IP4 127.0.0.1", info.getOrigin());
        assertEquals("Example stream", info.getSessionName());
        assertEquals("*", info.getControl());
        assertEquals(2, info.getMediaDescriptions().size());

        SdpParser.MediaDescription video = info.getVideoDescription();
        assertNotNull(video);
        assertEquals(0, video.getPort());
        assertEquals("RTP/AVP", video.getProtocol());
        assertEquals("96 97", video.getFormat());
        assertEquals("96", video.getPayloadType());
        assertEquals("H264/90000", video.getRtpMap());
        assertEquals("packetization-mode=1;profile-level-id=42e01f", video.getFmtp());
        assertEquals("trackID=0", video.getControl());

        SdpParser.MediaDescription audio = info.getAudioDescription();
        assertNotNull(audio);
        assertEquals(49170, audio.getPort());
        assertEquals("0", audio.getPayloadType());
        assertNull(audio.getRtpMap());
    }

    @Test
    void modelDefensivelyCopiesAndExposesImmutableCollections() {
        List<SdpParser.MediaDescription> media = new ArrayList<>();
        media.add(new SdpParser.MediaDescription(
                SdpParser.MediaType.VIDEO, 5004, "RTP/AVP", "96", "96", "H264/90000", "track"));

        SdpParser.SdpInfo info = new SdpParser.SdpInfo("0", "-", "test", media);
        media.clear();

        assertTrue(info.hasMedia());
        assertThrows(UnsupportedOperationException.class,
                () -> info.getMediaDescriptions().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> info.getMediaDescriptionsByType("video").clear());
    }

    @Test
    void invalidMediaSectionDoesNotPromoteItsAttributesToSessionScope() {
        String sdp = "v=0\r\n"
                + "o=- 1 1 IN IP4 127.0.0.1\r\n"
                + "s=Invalid media isolation\r\n"
                + "t=0 0\r\n"
                + "m=video invalid RTP/AVP 96\r\n"
                + "a=control:poisoned-aggregate\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "m=audio 0 RTP/AVP 97\r\n"
                + "a=control:trackID=1\r\n";

        SdpParser.SdpInfo info = SdpParser.parse(sdp);

        assertNull(info.getControl());
        assertEquals(1, info.getMediaDescriptions().size());
        assertEquals("trackID=1", info.getAudioDescription().getControl());
    }
}
