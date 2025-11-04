package ff.jnezha.jnt;

import ff.jnezha.jnt.body.JntResponse;
import ff.jnezha.jnt.utils.HttpType;

import java.net.Proxy;
import java.util.Map;

/**
 * @Copyright © 2020 sanbo Inc. All rights reserved.
 * @Description: 网络请求工具类
 * @Version: 1.0
 * @Create: 2020-12-16 14:19:02
 * @Author: sanbo
 */
public class Jnt {

    /**
     * <p>
     * getVersion.
     * </p>
     *
     * @return a {@link String} object.
     */
    public static String getVersion() {
        return NJnt.version();
    }

    public static String get(String requestUrl) {
        return get(requestUrl, null);
    }

    public static String get(String requestUrl, Map<String, String> reqHeaderMap) {
        return get(requestUrl, null, reqHeaderMap);
    }

    public static String get(String requestUrl, Proxy proxy, Map<String, String> reqHeaderMap) {
        return request(HttpType.GET, TIME_DEFAULT, requestUrl, proxy, reqHeaderMap, null);
    }

    public static String post(String requestUrl) {
        return post(requestUrl, null);
    }

    public static String post(String requestUrl, Map<String, String> reqHeaderMap) {
        return post(requestUrl, reqHeaderMap, null);
    }

    public static String post(String requestUrl, Map<String, String> reqHeaderMap, String data) {
        return post(requestUrl, null, reqHeaderMap, data);
    }

    public static String post(String requestUrl, Proxy proxy, Map<String, String> reqHeaderMap, String data) {
        return request(HttpType.POST, TIME_DEFAULT, requestUrl, proxy, reqHeaderMap, data);
    }

    /************************************************************************/

    public static JntResponse getResp(String requestUrl) {
        return getResp(requestUrl, null);
    }

    public static JntResponse getResp(String requestUrl, int timeout) {
        return getResp(requestUrl, null, timeout);
    }

    public static JntResponse getResp(String requestUrl, Map<String, String> reqHeaderMap) {
        return getResp(requestUrl, reqHeaderMap, TIME_DEFAULT);
    }

    public static JntResponse getResp(String requestUrl, Map<String, String> reqHeaderMap, int timeout) {
        return getResp(requestUrl, reqHeaderMap, timeout, 1);
    }

    public static JntResponse getResp(String requestUrl, Map<String, String> reqHeaderMap, int timeout, int tryTime) {
        return requestResp(HttpType.GET, timeout, requestUrl, null, reqHeaderMap, null, tryTime);
    }

    public static JntResponse postResp(String requestUrl, Map<String, String> reqHeaderMap, String data) {
        return postResp(requestUrl, reqHeaderMap, data, TIME_DEFAULT);
    }

    public static JntResponse postResp(String requestUrl, Map<String, String> reqHeaderMap, String data, int timeout) {
        return requestResp(HttpType.POST, timeout, requestUrl, null, reqHeaderMap, data);
    }

    public static JntResponse postResp(String requestUrl, Map<String, String> reqHeaderMap, String data, int timeout,
            int tryTime) {
        return requestResp(HttpType.POST, timeout, requestUrl, null, reqHeaderMap, data, tryTime);
    }

    public static JntResponse requestResp(String method, String requestUrl, Map<String, String> reqHeaderMap,
            String data) {
        return requestResp(method, requestUrl, null, reqHeaderMap, data);
    }

    public static JntResponse requestResp(String method, String requestUrl, Proxy proxy,
            Map<String, String> reqHeaderMap, String data) {
        return requestResp(method, TIME_DEFAULT, requestUrl, proxy, reqHeaderMap, data);
    }

    public static JntResponse requestResp(String method, int timeout, String requestUrl, Proxy proxy,
            Map<String, String> reqHeaderMap, String data) {
        return requestResp(method, timeout, requestUrl, proxy, reqHeaderMap, data, 1);
    }

    public static JntResponse requestResp(String method, int timeout, String requestUrl, Proxy proxy,
            Map<String, String> reqHeaderMap, String data, int trytime) {

        return NJnt.url(requestUrl).timeout(timeout).proxy(proxy)
                .header(reqHeaderMap).body(data).retryCount(trytime).request(method);
    }

    /************************************************************************/

    /**
     * request:
     * * 1. a).getConnection b).parser args and add RequestProperty 3).connect
     * * 2. a).post data b).listen the code,
     * * 3. process failed case or success case(parser the response)
     *
     * @param method       网络请求方式
     * @param timeout      网络请求超时时间
     * @param requestUrl   请求链接
     * @param proxy        代理
     * @param reqHeaderMap HTTP请求头键值对
     * @param data         请求数据
     * @return a {@link String} object.
     */
    public static String request(String method, int timeout, String requestUrl, Proxy proxy,
            Map<String, String> reqHeaderMap, String data) {
        JntResponse resp = requestResp(method, timeout, requestUrl, proxy, reqHeaderMap, data);
        String input = resp.getInputStream();
        if (input == null || input.trim().isEmpty()) {
            return resp.getErrorStream();
        } else {
            return input;
        }

    }

    private static int TIME_DEFAULT = 10 * 1000;
}
