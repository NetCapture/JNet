package ff.jnezha.jnt.utils;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * Copyright © 2020 sanbo Inc. All rights reserved.
 * Description: 证书信任类，忽略证书检查
 * Version: 1.0
 * Create: 2020-12-16 14:02:31
 * Author: sanbo
 */
class ClX509TrustManager implements X509TrustManager {
    /**
     * {@inheritDoc}
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}

