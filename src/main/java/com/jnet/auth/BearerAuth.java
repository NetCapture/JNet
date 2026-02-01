package com.jnet.auth;

import com.jnet.core.Request;

/**
 * Bearer Token 认证实现
 */
public class BearerAuth implements Auth {
    private final String headerValue;

    public BearerAuth(String token) {
        this.headerValue = "Bearer " + token;
    }

    @Override
    public Request apply(Request request) {
        return request.toBuilder()
                .header("Authorization", headerValue)
                .build();
    }
}
