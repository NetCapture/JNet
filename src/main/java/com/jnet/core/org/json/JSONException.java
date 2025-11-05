package com.jnet.core.org.json;

/**
 * The JSONException is thrown by a JSON package when a problem is encountered.
 * @author JSON.org
 * @version 2010-12-24
 */
public class JSONException extends Exception {
    private static final long serialVersionUID = 0;
    private Throwable cause;

    /**
     * Constructs a JSONException with an explanatory message.
     * @param message Detail about the reason for the exception.
     */
    public JSONException(String message) {
        super(message);
    }

    /**
     * Constructs a JSONException with an explanatory message and cause.
     * @param message Detail about the reason for the exception.
     * @param cause The cause.
     */
    public JSONException(String message, Throwable cause) {
        super(message);
        this.cause = cause;
    }

    /**
     * Returns the cause of this exception or null if the cause is nonexistent or unknown.
     * @return The cause of this exception or null if the cause is nonexistent or unknown.
     */
    @Override
    public Throwable getCause() {
        return this.cause;
    }
}
