package ff.jnezha.jnt;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;

import com.jnet.core.Response;

/**
 * 响应对象 - 老版本兼容 API
 * 提供与之前版本相同的 Response 接口
 *
 * @author sanbo
 * @version 3.0.0
 */
@Deprecated
public class JntResponse {
    private int code;
    private String message;
    private String body;
    private Map<String, String> headers;
    private boolean success;
    private String errorMessage;

    private JntResponse() {
    }

    /**
     * 从新版本 Response 创建
     */
    public static JntResponse fromResponse(Response response) {
        Objects.requireNonNull(response, "response");
        JntResponse resp = new JntResponse();
        resp.code = response.getCode();
        resp.message = response.getMessage();
        resp.body = response.getBody();
        resp.headers = Collections.unmodifiableMap(new LinkedHashMap<>(response.getHeaders()));
        resp.success = response.isSuccessful();
        return resp;
    }

    /**
     * 创建失败响应
     */
    public static JntResponse failure(String errorMessage) {
        JntResponse resp = new JntResponse();
        resp.success = false;
        resp.errorMessage = errorMessage;
        resp.code = 0;
        resp.message = "Request Failed";
        return resp;
    }

    /**
     * 创建成功响应
     */
    public static JntResponse success(String body) {
        JntResponse resp = new JntResponse();
        resp.success = true;
        resp.body = body;
        resp.code = 200;
        resp.message = "OK";
        return resp;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? Collections.emptyMap() : headers;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "JntResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", success=" + success +
                ", bodyLength=" + (body == null ? 0 : body.length()) +
                '}';
    }
}
