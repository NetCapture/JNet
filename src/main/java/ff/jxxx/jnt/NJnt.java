package ff.jxxx.jnt;

import com.jnet.core.JNet;
import java.util.Map;

/**
 * JNet 新版本兼容 API (NJnt)
 * 提供与之前 NJnt 风格相同的 API 接口
 *
 * @author sanbo
 * @version 3.0.0
 * @deprecated 使用 {@link com.jnet.core.JNet} 代替
 */
@Deprecated
public class NJnt {

    /**
     * 获取 HTTP 请求构建器
     */
    public static class XX {
        private final String url;
        private String method = "GET";
        private String body;
        private Map<String, String> headers;

        private XX(String url) {
            this.url = url;
        }

        public XX get() {
            this.method = "GET";
            return this;
        }

        public XX post() {
            this.method = "POST";
            return this;
        }

        public XX put() {
            this.method = "PUT";
            return this;
        }

        public XX delete() {
            this.method = "DELETE";
            return this;
        }

        public XX url(String url) {
            XX newXX = new XX(url);
            newXX.method = this.method;
            newXX.body = this.body;
            newXX.headers = this.headers;
            return newXX;
        }

        public XX body(String data) {
            this.body = data;
            return this;
        }

        public XX header(String key, String value) {
            if (this.headers == null) {
                this.headers = new java.util.HashMap<>();
            }
            this.headers.put(key, value);
            return this;
        }

        public XX headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers = headers;
            }
            return this;
        }

        public String exec() {
            return JNet.request(method, url, body, headers);
        }
    }

    /**
     * 创建 GET 请求
     */
    public static XX get() {
        return new XX("http://localhost").get();
    }

    /**
     * 创建指定 URL 的请求
     */
    public static XX get(String url) {
        XX xx = new XX(url);
        return xx.get();
    }

    /**
     * 创建 POST 请求
     */
    public static XX post(String url) {
        XX xx = new XX(url);
        return xx.post();
    }

    /**
     * 创建 PUT 请求
     */
    public static XX put(String url) {
        XX xx = new XX(url);
        return xx.put();
    }

    /**
     * 创建 DELETE 请求
     */
    public static XX delete(String url) {
        XX xx = new XX(url);
        return xx.delete();
    }
}
