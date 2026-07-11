package com.jnet.core;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Fixed ProxySelector for an HTTP proxy or an explicit direct connection.
 * 
 * @author sanbo
 * @version 3.0.0
 */
class JNetProxySelector extends ProxySelector {
    private final Proxy proxy;

    public JNetProxySelector(Proxy proxy) {
        this.proxy = java.util.Objects.requireNonNull(proxy, "Proxy cannot be null");
        if (proxy.type() == Proxy.Type.SOCKS) {
            throw new IllegalArgumentException("SOCKS proxies are not supported by JDK HttpClient");
        }
    }

    @Override
    public List<Proxy> select(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        // 返回配置的代理
        return Collections.singletonList(proxy);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        // The originating HTTP operation reports the connection failure.
    }
}
