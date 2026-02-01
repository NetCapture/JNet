package com.jnet.auth;

import com.jnet.core.JNetUtils;
import com.jnet.core.Request;

/**
 * Basic 认证实现
 */
public class BasicAuth implements Auth {
    private final String encoded;

    public BasicAuth(String username, String password) {
        String credentials = username + ":" + password;
        this.encoded = "Basic " + JNetUtils.encodeBase64(credentials);
    }

    @Override
    public Request apply(Request request) {
        return request.toBuilder()
                .header("Authorization", encoded)
                .build();
    }
}
