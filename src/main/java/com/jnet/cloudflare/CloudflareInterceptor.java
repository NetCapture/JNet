package com.jnet.cloudflare;

import com.jnet.core.Interceptor;
import com.jnet.core.Request;
import com.jnet.core.Response;

import java.io.IOException;

/**
 * Cloudflare Challenge 拦截器
 * 自动检测和处理 Cloudflare 挑战页面
 */
public class CloudflareInterceptor implements Interceptor {
    private final int maxRetries;
    private final long retryDelay;

    public CloudflareInterceptor() {
        this(3, 5000); // 默认重试3次，每次等待5秒
    }

    public CloudflareInterceptor(int maxRetries, long retryDelay) {
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        int retries = 0;
        while (isCloudflareChallenge(response) && retries < maxRetries) {
            retries++;
            
            // 等待 Cloudflare JavaScript 执行
            sleep(retryDelay);
            
            // 重试请求
            response = chain.proceed(request);
        }

        return response;
    }

    /**
     * 检测是否为 Cloudflare 挑战页面
     */
    private boolean isCloudflareChallenge(Response response) {
        // 检查 503 状态码和 CF-Ray 头
        if (response.getCode() == 503) {
            String cfRay = response.getHeaders().get("CF-Ray");
            String server = response.getHeaders().get("Server");
            
            if (cfRay != null || (server != null && server.contains("cloudflare"))) {
                return true;
            }
        }

        // 检查响应体中是否包含 Cloudflare 挑战标识
        String body = response.getBody();
        if (body != null) {
            return body.contains("Checking your browser") 
                || body.contains("Just a moment") 
                || body.contains("cf-browser-verification")
                || body.contains("__cf_chl_jschl_tk__");
        }

        return false;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sleep interrupted", e);
        }
    }
}
