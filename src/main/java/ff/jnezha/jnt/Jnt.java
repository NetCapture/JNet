package ff.jnezha.jnt;

import java.util.Map;

import com.jnet.core.JNet;

/**
 * Deprecated v3 facade kept only for artifacts that already exposed this package.
 * It is not source-compatible with every historical v2 API.
 *
 * @author sanbo
 * @version 3.0.0
 * @deprecated 使用 {@link com.jnet.core.JNet} 代替
 */
@Deprecated
public class Jnt {

    /**
     * 兼容老 API - GET 请求
     * @param url 请求 URL
     * @return 响应内容
     */
    public static String get(String url) {
        return JNet.get(url);
    }

    /**
     * 兼容老 API - GET 请求
     * @param url 请求 URL
     * @param headers 请求头
     * @return 响应内容
     */
    public static String get(String url, Map<String, String> headers) {
        return JNet.get(url, headers, (Map<String, String>) null);
    }

    /**
     * 兼容老 API - POST 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应内容
     */
    public static String post(String url, String data) {
        return JNet.post(url, data);
    }

    /**
     * 兼容老 API - POST 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应内容
     */
    public static String post(String url, String data, Map<String, String> headers) {
        return JNet.request("POST", url, data, headers);
    }

    /**
     * 兼容老 API - PUT 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应内容
     */
    public static String put(String url, String data) {
        return JNet.put(url, data);
    }

    /**
     * 兼容老 API - PUT 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应内容
     */
    public static String put(String url, String data, Map<String, String> headers) {
        return JNet.request("PUT", url, data, headers);
    }

    /**
     * 兼容老 API - DELETE 请求
     * @param url 请求 URL
     * @return 响应内容
     */
    public static String delete(String url) {
        return JNet.delete(url);
    }

    /**
     * 兼容老 API - DELETE 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应内容
     */
    public static String delete(String url, String data) {
        return JNet.request("DELETE", url, data);
    }

    /**
     * 兼容老 API - DELETE 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应内容
     */
    public static String delete(String url, String data, Map<String, String> headers) {
        return JNet.request("DELETE", url, data, headers);
    }

    /**
     * 兼容老 API - 自定义请求
     * @param method HTTP 方法
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应内容
     */
    public static String request(String method, String url, String data) {
        return JNet.request(method, url, data);
    }

    /**
     * 兼容老 API - 自定义请求
     * @param method HTTP 方法
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应内容
     */
    public static String request(String method, String url, String data, Map<String, String> headers) {
        return JNet.request(method, url, data, headers);
    }

    /**
     * 兼容老 API - 获取响应对象
     * @param url 请求 URL
     * @return 响应对象
     */
    public static JntResponse getResp(String url) {
        return response("GET", url, null, null);
    }

    /**
     * 兼容老 API - 获取响应对象
     * @param url 请求 URL
     * @param headers 请求头
     * @return 响应对象
     */
    public static JntResponse getResp(String url, Map<String, String> headers) {
        return response("GET", url, null, headers);
    }

    /**
     * 兼容老 API - 获取 POST 响应对象
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应对象
     */
    public static JntResponse postResp(String url, String data) {
        return response("POST", url, data, null);
    }

    /**
     * 兼容老 API - 获取 POST 响应对象
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应对象
     */
    public static JntResponse postResp(String url, String data, Map<String, String> headers) {
        return response("POST", url, data, headers);
    }

    /**
     * 兼容老 API - 获取自定义请求响应对象
     * @param method HTTP 方法
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应对象
     */
    public static JntResponse requestResp(String method, String url, String data) {
        return response(method, url, data, null);
    }

    /**
     * 兼容老 API - 获取自定义请求响应对象
     * @param method HTTP 方法
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应对象
     */
    public static JntResponse requestResp(String method, String url, String data, Map<String, String> headers) {
        return response(method, url, data, headers);
    }

    private static JntResponse response(String method, String url, String data,
            Map<String, String> headers) {
        try {
            return JntResponse.fromResponse(JNet.requestResponse(method, url, data, headers));
        } catch (Exception e) {
            return JntResponse.failure(e.getMessage());
        }
    }
}
