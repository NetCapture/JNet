package com.jnet.rtsp;

/**
 * RTSP Methods
 * All RTSP methods as defined by RFC 2326 (1998)
 *
 * @author sanbo
 * @version 3.5.0
 */
public enum RtspMethod {

    OPTIONS,
    DESCRIBE,
    SETUP,
    PLAY,
    PAUSE,
    TEARDOWN,
    GET_PARAMETER,
    SET_PARAMETER,
    RECORD,
    ANNOUNCE;

    /**
     * Get HTTP method string
     */
    public String getMethod() {
        return name();
    }

    /**
     * Check if this is a request or response
     */
    public boolean isRequest() {
        return this != GET_PARAMETER && this != SET_PARAMETER && this != RECORD;
    }
}
