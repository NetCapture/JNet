package ff.jnezha.jnt.body;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Copyright © 2022 sanbo Inc. All rights reserved.
 * @Description: 请求回复
 * @Version: 1.0
 * @Create: 2022/1/4 1:06 PM
 * @author: sanbo
 */
public class JntResponse {


    private List<Throwable> mRunExceptions = null;
    private int responseCode = -1;
    private String mResponseMessage = "";
    private Map<String, List<String>> mRequestHeaders = null;
    private Map<String, List<String>> mResponseHeaders = null;
    private String mRequestUrl = null;
    private String mRequestMethod = null;
    private String mInputStream = null;
    private String mErrorStream = null;
    private boolean instanceFollowRedirects = false;
    // 耗时
    private long mTimingPhases = -1L;

    public JntResponse() {
        mRunExceptions = new ArrayList<Throwable>();
        mResponseMessage = "";
        responseCode = -1;
        mRequestHeaders = null;
        mResponseHeaders = null;
        mInputStream = null;
        mErrorStream = null;
        instanceFollowRedirects = false;
        mRequestUrl = null;
        mRequestMethod = null;
        mTimingPhases = -1L;
    }

    public String getRequestMethod() {
        return mRequestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.mRequestMethod = requestMethod;
    }

    public void setRequestUrl(String reqUrl) {
        mRequestUrl = reqUrl;
    }

    /**
     * 设置运行时异常
     *
     * @param e
     */
    public void setRunException(Throwable e) {
        if (!mRunExceptions.contains(e)) {
            mRunExceptions.add(e);
        }
    }

    public void setResponseCode(int code) {
        responseCode = code;
    }

    public void setResponseMessage(String responseMessage) {
        mResponseMessage = responseMessage;
    }

    public void setResponseHeaders(Map<String, List<String>> headerFields) {
        mResponseHeaders = headerFields;
    }

    public void setRequestHeaders(Map<String, List<String>> headerFields) {
        mRequestHeaders = headerFields;
    }

    public void setInputStream(String ist) {
        mInputStream = ist;
    }

    public void setErrorStream(String est) {
        mErrorStream = est;
    }


    public void setInstanceFollowRedirects(boolean ifr) {
        instanceFollowRedirects = ifr;
    }


    public List<Throwable> getRunExceptions() {
        return mRunExceptions;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return mResponseMessage;
    }

    public Map<String, List<String>> getResponseHeaders() {
        return mResponseHeaders;
    }

    public String getInputStream() {
        return mInputStream;
    }

    public String getErrorStream() {
        return mErrorStream;
    }


    public boolean isInstanceFollowRedirects() {
        return instanceFollowRedirects;
    }

    public String getRequestUrl() {
        return mRequestUrl;
    }

    public void setTimingPhases(long timingPhases) {
        mTimingPhases = timingPhases;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("JntResponse{\n");
        sb.append("  Request URL: ").append(mRequestUrl != null ? mRequestUrl : "null").append("\n");
        sb.append("  Method: ").append(mRequestMethod != null ? mRequestMethod : "null").append("\n");
        sb.append("  Response Code: ").append(responseCode).append("\n");
        sb.append("  Response Message: ").append(mResponseMessage != null ? mResponseMessage : "null").append("\n");
        sb.append("  Duration: ").append(mTimingPhases).append("ms\n");

        if (mResponseHeaders != null && mResponseHeaders.size() > 0) {
            sb.append("  Response Headers:{\n");
            for (Map.Entry<String, List<String>> entry : mResponseHeaders.entrySet()) {
                String key = entry.getKey();
                List<String> value = entry.getValue();
                if (key == null || key.trim().isEmpty()) {
                    sb.append("    null-key: ").append(value != null ? value.toString() : "null").append("\n");
                } else {
                    sb.append("    ").append(key).append(": ").append(value != null ? value.toString() : "null").append("\n");
                }
            }
            sb.append("  }\n");
        }

        if (mRequestHeaders != null && mRequestHeaders.size() > 0) {
            sb.append("  Request Headers:{\n");
            for (Map.Entry<String, List<String>> entry : mRequestHeaders.entrySet()) {
                String key = entry.getKey();
                List<String> value = entry.getValue();
                if (key == null || key.trim().isEmpty()) {
                    sb.append("    null-key: ").append(value != null ? value.toString() : "null").append("\n");
                } else {
                    sb.append("    ").append(key).append(": ").append(value != null ? value.toString() : "null").append("\n");
                }
            }
            sb.append("  }\n");
        }

        sb.append("  Input Stream: ").append(mInputStream != null ? mInputStream : "null").append("\n");
        sb.append("  Error Stream: ").append(mErrorStream != null ? mErrorStream : "null").append("\n");
        sb.append("  Follow Redirects: ").append(instanceFollowRedirects).append("\n");

        if (mRunExceptions != null && mRunExceptions.size() > 0) {
            sb.append("  Exceptions:[\n");
            for (int i = 0; i < mRunExceptions.size(); i++) {
                sb.append("    ").append(i).append(": ").append(getStackTrace(mRunExceptions.get(i))).append("\n");
            }
            sb.append("  ]\n");
        }

        sb.append("}");
        return sb.toString();
    }

    public static String getStackTrace(Throwable e) {
        if (e == null) {
            return "null";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }


}
