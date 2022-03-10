package ff.jnezha.jnt;

import ff.jnezha.jnt.body.JntResponse;
import ff.jnezha.jnt.body.ReqImpl;
import ff.jnezha.jnt.utils.HttpType;
import ff.jnezha.jnt.utils.Logger;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;

/**
 * Copyright © 2020 analysys Inc. All rights reserved.
 * Description: 网络请求工具类
 * Version: 1.0
 * Create: 2020-12-16 14:19:02
 * Author: sanbo
 */
public class NJnt {

    public static NJnt setDebug(boolean debug) {
        if (initConfig()) {
            mConfig.debugConfig = debug;
        }
        return NJnt.getInstance();
    }

    public static NJnt retry(int retryTime) {
        if (initConfig()) {
            mConfig.retryTime_Config = retryTime;
        }
        return NJnt.getInstance();
    }

    public static NJnt url(String url) {
        if (initConfig()) {
            mConfig.url = url;
        }
        return NJnt.getInstance();
    }

    public static NJnt body(String data) {
        if (initConfig()) {
            mConfig.data_Config = data;
        }
        return NJnt.getInstance();

    }

    public static NJnt logtag(String tag) {
        if (initConfig()) {
            mConfig.TAG = tag;
        }
        return NJnt.getInstance();
    }


    public static NJnt header(Map<String, String> headers) {
        if (initConfig()) {
            mConfig.headers_Config = headers;
        }
        return NJnt.getInstance();
    }

    public static NJnt timeout(int timeout) {
        if (initConfig()) {
            mConfig.timeout_Config = timeout;
        }
        return NJnt.getInstance();
    }

    public static NJnt proxy(String ip, int port) {
        return proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port)));
    }

    public static NJnt proxy(Proxy.Type type, String ip, int port) {
        return proxy(new Proxy(type, new InetSocketAddress(ip, port)));
    }

    public static NJnt proxy(Proxy p) {
        if (initConfig()) {
            mConfig.proxy_Config = p;
        }
        return NJnt.getInstance();
    }

    public JntResponse get() {
        return request(HttpType.GET);
    }

    public JntResponse post() {
        return request(HttpType.POST);
    }

    public JntResponse put() {
        return request(HttpType.PUT);
    }

    public JntResponse delete() {
        return request(HttpType.DELETE);
    }


    public JntResponse request(String mMethod) {
        try {
            // makesure init config.
            if (initConfig()) {
                mConfig.method = mMethod;
            }
            // if the url is null, will return resp
            return ReqImpl.request(mConfig);
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            mConfig = null;
        }
        // makesure resp
        return new JntResponse();
    }

    private static boolean initConfig() {
        if (mConfig == null) {
            mConfig = new JntConfig();
        }
        return mConfig != null;
    }


    public static boolean isDebug() {
        initConfig();
        return mConfig.debugConfig;
    }

    public static String getVersion() {
        return version();
    }

    public static String version() {
        initConfig();
        return JntFormatVersion.version();
    }


    /********************* get instance begin **************************/
    private static NJnt getInstance() {
        return Holder.Instance;
    }

    private static class Holder {
        private static final NJnt Instance = new NJnt();
    }

    private NJnt() {
    }

    /********************* get instance end **************************/
    private static JntConfig mConfig = null;

}
