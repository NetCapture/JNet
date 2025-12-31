package com.jnet.core.org.json;

/**
 * Minimal implementation of JSONException to avoid external dependencies.
 */
public class JSONException extends RuntimeException {
    public JSONException(String message) {
        super(message);
    }

    public JSONException(String message, Throwable cause) {
        super(message, cause);
    }

    public JSONException(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
