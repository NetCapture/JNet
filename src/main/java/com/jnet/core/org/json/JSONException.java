package com.jnet.core.org.json;

/**
 * Minimal implementation of JSONException to avoid external dependencies.
 */
public class JSONException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JSONException(String message) {
        super(message);
    }

    public JSONException(String message, Throwable cause) {
        super(message, cause);
    }

    public JSONException(Throwable cause) {
        super(cause == null ? null : cause.getMessage(), cause);
    }

    @Override
    public Throwable getCause() {
        return super.getCause();
    }
}
