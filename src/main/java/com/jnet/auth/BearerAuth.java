package com.jnet.auth;

import com.jnet.core.Request;

/**
 * Bearer Token 认证实现
 */
public class BearerAuth implements Auth {
    private final String headerValue;

    public BearerAuth(String token) {
        this.headerValue = createHeaderValue(token);
    }

    /** Builds a validated Bearer Authorization header value. */
    public static String createHeaderValue(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token must not be null or empty");
        }
        for (int i = 0; i < token.length(); i++) {
            char current = token.charAt(i);
            if (current <= 0x20 || current == 0x7f) {
                throw new IllegalArgumentException("Token contains whitespace or a control character");
            }
        }
        return "Bearer " + token;
    }

    @Override
    public Request apply(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        return request.toBuilder()
                .header("Authorization", headerValue)
                .build();
    }
}
