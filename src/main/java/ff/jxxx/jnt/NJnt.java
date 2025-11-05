package ff.jxxx.jnt;

import com.jnet.core.*;
import java.util.Map;

/**
 * JNet 新版本兼容 API (NJnt)
 * 提供与之前 NJnt 风格相同的 API 接口
 *
 * @author sanbo
 * @version 3.0.0
 * @deprecated 使用 {@link com.jnet.core.JNetClient} 代替
 */
@Deprecated
public class NJnt {

    /**
     * 获取 HTTP 请求构建器
     */
    public static class XX {
        private final JNetClient client;
        private Request.Builder builder;

        private XX(String url) {
            this.client = JNetClient.getInstance();
            this.builder = client.newGet(url);
        }

        public XX get() {
            return this;
        }

        public XX post() {
            this.builder = client.newPost(builder.build().getUrlString());
            return this;
        }

        public XX put() {
            this.builder = client.newPut(builder.build().getUrlString());
            return this;
        }

        public XX delete() {
            this.builder = client.newDelete(builder.build().getUrlString());
            return this;
        }

        public XX url(String url) {
            return new XX(url);
        }

        public XX body(String data) {
            builder.body(data);
            return this;
        }

        public XX header(String key, String value) {
            builder.header(key, value);
            return this;
        }

        public XX headers(Map<String, String> headers) {
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        public Request build() {
            return builder.build();
        }

        public String exec() {
            try {
                Response response = build().newCall().execute();
                return response.getBody();
            } catch (Exception e) {
                throw new RuntimeException("Request failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 创建 GET 请求
     */
    public static XX get() {
        return new XX("http://localhost");
    }

    /**
     * 创建指定 URL 的请求
     */
    public static XX get(String url) {
        return new XX(url);
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
