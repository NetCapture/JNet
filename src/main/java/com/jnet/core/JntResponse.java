package com.jnet.core;

import java.util.*;

/**
 * 请求响应类 (兼容旧API)
 * 替代 ff.jnezha.jnt.body.JntResponse
 * @deprecated 使用 com.jnet.core.Response
 */
@Deprecated
public class JntResponse {

    private List<Throwable> mRunExceptions = new ArrayList<>();
    private int responseCode = -1;
    private String mResponseMessage = "";
    private Map<String, List<String>> mRequestHeaders = null;
    private Map<String, List<String>> mResponseHeaders = null;
    private String mRequestUrl = null;
    private String mRequestMethod = null;
    private String mInputStream = null;
    private String mErrorStream = null;
    private boolean instanceFollowRedirects = false;
    private long mTimingPhases = -1L;

    public JntResponse() {}

    public void setRequestMethod(String requestMethod) {
        this.mRequestMethod = requestMethod;
    }

    public void setRequestUrl(String reqUrl) {
        this.mRequestUrl = reqUrl;
    }

    public void setRunException(Throwable e) {
        if (!mRunExceptions.contains(e)) {
            mRunExceptions.add(e);
        }
    }

    public void setResponseCode(int code) {
        this.responseCode = code;
    }

    public void setResponseMessage(String responseMessage) {
        this.mResponseMessage = responseMessage;
    }

    public void setResponseHeaders(Map<String, List<String>> headerFields) {
        this.mResponseHeaders = headerFields;
    }

    public void setRequestHeaders(Map<String, List<String>> headerFields) {
        this.mRequestHeaders = headerFields;
    }

    public void setInputStream(String ist) {
        this.mInputStream = ist;
    }

    public void setErrorStream(String est) {
        this.mErrorStream = est;
    }

    public void setInstanceFollowRedirects(boolean ifr) {
        this.instanceFollowRedirects = ifr;
    }

    public void setTimingPhases(long timingPhases) {
        this.mTimingPhases = timingPhases;
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

    public String getRequestMethod() {
        return mRequestMethod;
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
        sb.append("  Input Stream: ").append(mInputStream != null ? mInputStream : "null").append("\n");
        sb.append("  Error Stream: ").append(mErrorStream != null ? mErrorStream : "null").append("\n");
        sb.append("  Follow Redirects: ").append(instanceFollowRedirects).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
