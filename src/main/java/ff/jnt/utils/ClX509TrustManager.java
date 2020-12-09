package ff.jnt.utils;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * @Copyright © 2020 analysys Inc. All rights reserved.
 * @Description: 证书信任管理器
 * @Version: 1.0
 * @Create: 2020-12-08 15:17:33
 * @author: sanbo
 */
class ClX509TrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {

    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}

