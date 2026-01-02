package com.jnet.core;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * 自定义 ProxySelector，支持 HTTP 和 SOCKS 代理
 * 
 * @author sanbo
 * @version 3.0.0
 */
class JNetProxySelector extends ProxySelector {
    private final Proxy proxy;

    public JNetProxySelector(Proxy proxy) {
        this.proxy = java.util.Objects.requireNonNull(proxy, "Proxy cannot be null");
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
        // 记录连接失败,但不抛出异常
        System.err.println("Proxy connection failed for " + uri + ": " + ioe.getMessage());
    }
}
