package com.jnet.core;

import java.net.Proxy;
import java.util.Map;

/**
 * 请求配置类
 * 替代 ff.jnezha.jnt.JntConfig
 * @deprecated 使用 com.jnet.core.Request.Builder
 */
@Deprecated
public class JntConfig {

    public String user_tag = null;
    public String description = null;
    public String url = null;
    public String method = null;
    public int timeout_Config = 10 * 1000;
    public Proxy proxy_Config = null;
    public Map<String, String> headers_Config = null;
    public String data_Config = null;
    public int retryTime_Config = 3;
    public boolean debugConfig = true;

    public JntConfig() {
    }
}
