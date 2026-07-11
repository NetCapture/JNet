package com.jnet.auth;

import com.jnet.core.JNetUtils;
import com.jnet.core.Request;

/**
 * Basic 认证实现
 */
public class BasicAuth implements Auth {
    private final String encoded;

    public BasicAuth(String username, String password) {
        this.encoded = createHeaderValue(username, password);
    }

    /** Builds a validated Basic Authorization header value. */
    public static String createHeaderValue(String username, String password) {
        requireCredential(username, "Username");
        requireCredential(password, "Password");
        if (username.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Username must not contain ':'");
        }
        String credentials = username + ":" + password;
        return "Basic " + JNetUtils.encodeBase64(credentials);
    }

    @Override
    public Request apply(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        return request.toBuilder()
                .header("Authorization", encoded)
                .build();
    }

    private static void requireCredential(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == 0x7f || current < 0x20) {
                throw new IllegalArgumentException(label + " contains a prohibited control character");
            }
        }
    }
}
