package ff.jnezha.jnt.utils;

import javax.net.ssl.*;

/**
 * @Copyright © 2020 analysys Inc. All rights reserved.
 * @Description: SSL需要的配置, 已经包含: 请求获取SSL工厂和 证书验证器
 * @Version: 1.0
 * @Create: 2020-12-08 15:18:15
 * @author: sanbo
 */
public class SSLConfig {
    public static final HostnameVerifier NOT_VERYFY = new HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    };

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
