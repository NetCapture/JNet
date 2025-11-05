package ff.jxxx.jnt;

import com.jnet.core.*;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

/**
 * JNet 老版本兼容 API
 * 提供与之前版本相同的 API 接口，确保老项目可以无缝升级
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
        return get(url, (Map<String, String>) null);
    }

    /**
     * 兼容老 API - GET 请求
     * @param url 请求 URL
     * @param headers 请求头
     * @return 响应内容
     */
    public static String get(String url, Map<String, String> headers) {
        try {
            Request.Builder builder = JNetClient.getInstance().newGet(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            Request request = builder.build();
            Response response = request.newCall().execute();
            return response.getBody();
        } catch (IOException e) {
            throw new RuntimeException("GET request failed: " + e.getMessage(), e);
        }
    }

    /**
     * 兼容老 API - POST 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应内容
     */
    public static String post(String url, String data) {
        return post(url, data, (Map<String, String>) null);
    }

    /**
     * 兼容老 API - POST 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应内容
     */
    public static String post(String url, String data, Map<String, String> headers) {
        try {
            Request.Builder builder = JNetClient.getInstance().newPost(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            if (data != null) {
                builder.body(data);
            }
            Request request = builder.build();
            Response response = request.newCall().execute();
            return response.getBody();
        } catch (IOException e) {
            throw new RuntimeException("POST request failed: " + e.getMessage(), e);
        }
    }

    /**
     * 兼容老 API - PUT 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应内容
     */
    public static String put(String url, String data) {
        return put(url, data, (Map<String, String>) null);
    }

    /**
     * 兼容老 API - PUT 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应内容
     */
    public static String put(String url, String data, Map<String, String> headers) {
        try {
            Request.Builder builder = JNetClient.getInstance().newPut(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            if (data != null) {
                builder.body(data);
            }
            Request request = builder.build();
            Response response = request.newCall().execute();
            return response.getBody();
        } catch (IOException e) {
            throw new RuntimeException("PUT request failed: " + e.getMessage(), e);
        }
    }

    /**
     * 兼容老 API - DELETE 请求
     * @param url 请求 URL
     * @return 响应内容
     */
    public static String delete(String url) {
        return delete(url, (String) null);
    }

    /**
     * 兼容老 API - DELETE 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应内容
     */
    public static String delete(String url, String data) {
        return delete(url, data, (Map<String, String>) null);
    }

    /**
     * 兼容老 API - DELETE 请求
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应内容
     */
    public static String delete(String url, String data, Map<String, String> headers) {
        try {
            Request.Builder builder = JNetClient.getInstance().newDelete(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            if (data != null) {
                builder.body(data);
            }
            Request request = builder.build();
            Response response = request.newCall().execute();
            return response.getBody();
        } catch (IOException e) {
            throw new RuntimeException("DELETE request failed: " + e.getMessage(), e);
        }
    }

    /**
     * 兼容老 API - 自定义请求
     * @param method HTTP 方法
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应内容
     */
    public static String request(String method, String url, String data) {
        return request(method, url, data, (Map<String, String>) null);
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
        try {
            Request.Builder builder;
            switch (method.toUpperCase()) {
                case "GET":
                    builder = JNetClient.getInstance().newGet(url);
                    break;
                case "POST":
                    builder = JNetClient.getInstance().newPost(url);
                    break;
                case "PUT":
                    builder = JNetClient.getInstance().newPut(url);
                    break;
                case "DELETE":
                    builder = JNetClient.getInstance().newDelete(url);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            if (data != null) {
                builder.body(data);
            }
            Request request = builder.build();
            Response response = request.newCall().execute();
            return response.getBody();
        } catch (IOException e) {
            throw new RuntimeException("Request failed: " + e.getMessage(), e);
        }
    }

    /**
     * 兼容老 API - 获取响应对象
     * @param url 请求 URL
     * @return 响应对象
     */
    public static JntResponse getResp(String url) {
        return getResp(url, (Map<String, String>) null);
    }

    /**
     * 兼容老 API - 获取响应对象
     * @param url 请求 URL
     * @param headers 请求头
     * @return 响应对象
     */
    public static JntResponse getResp(String url, Map<String, String> headers) {
        try {
            Request.Builder builder = JNetClient.getInstance().newGet(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            Request request = builder.build();
            Response response = request.newCall().execute();
            return JntResponse.fromResponse(response);
        } catch (IOException e) {
            return JntResponse.failure(e.getMessage());
        }
    }

    /**
     * 兼容老 API - 获取 POST 响应对象
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应对象
     */
    public static JntResponse postResp(String url, String data) {
        return postResp(url, data, (Map<String, String>) null);
    }

    /**
     * 兼容老 API - 获取 POST 响应对象
     * @param url 请求 URL
     * @param data 请求数据
     * @param headers 请求头
     * @return 响应对象
     */
    public static JntResponse postResp(String url, String data, Map<String, String> headers) {
        try {
            Request.Builder builder = JNetClient.getInstance().newPost(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            if (data != null) {
                builder.body(data);
            }
            Request request = builder.build();
            Response response = request.newCall().execute();
            return JntResponse.fromResponse(response);
        } catch (IOException e) {
            return JntResponse.failure(e.getMessage());
        }
    }

    /**
     * 兼容老 API - 获取自定义请求响应对象
     * @param method HTTP 方法
     * @param url 请求 URL
     * @param data 请求数据
     * @return 响应对象
     */
    public static JntResponse requestResp(String method, String url, String data) {
        return requestResp(method, url, data, (Map<String, String>) null);
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
        try {
            Request.Builder builder;
            switch (method.toUpperCase()) {
                case "GET":
                    builder = JNetClient.getInstance().newGet(url);
                    break;
                case "POST":
                    builder = JNetClient.getInstance().newPost(url);
                    break;
                case "PUT":
                    builder = JNetClient.getInstance().newPut(url);
                    break;
                case "DELETE":
                    builder = JNetClient.getInstance().newDelete(url);
                    break;
                default:
                    return JntResponse.failure("Unsupported HTTP method: " + method);
            }

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            if (data != null) {
                builder.body(data);
            }
            Request request = builder.build();
            Response response = request.newCall().execute();
            return JntResponse.fromResponse(response);
        } catch (IOException e) {
            return JntResponse.failure(e.getMessage());
        }
    }
}
