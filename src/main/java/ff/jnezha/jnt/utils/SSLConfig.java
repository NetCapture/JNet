package ff.jnezha.jnt.utils;

import javax.net.ssl.*;

/**
 * Copyright © 2020 analysys Inc. All rights reserved.
 * Description: HTTPS请求响应的配置类
 * Version: 1.0
 * Create: 2020-12-16 14:20:21
 * Author: sanbo
 * @version $Id: $Id
 */
public class SSLConfig {
    /**
     * Constant <code>NOT_VERYFY</code>
     */
    public static final HostnameVerifier NOT_VERYFY = new HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    };

    /**
     * <p>getSSLFactory.</p>
     *
     * @return a {@link javax.net.ssl.SSLSocketFactory} object.
     */
    public static SSLSocketFactory getSSLFactory() {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, new TrustManager[]{new ClX509TrustManager()}, new java.security.SecureRandom());
            return sslcontext.getSocketFactory();
        } catch (Throwable ignored) {
        }
        return null;
    }

}
