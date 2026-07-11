package com.jnet.cloudflare;

import com.jnet.core.Interceptor;
import com.jnet.core.Request;
import com.jnet.core.Response;

import java.io.IOException;
import java.util.Locale;

/**
 * Cloudflare Challenge 拦截器
 * Detects likely challenge responses and retries safe methods after a delay.
 * It does not execute JavaScript or bypass interactive challenges.
 */
public class CloudflareInterceptor implements Interceptor {
    private final int maxRetries;
    private final long retryDelay;

    public CloudflareInterceptor() {
        this(3, 5000); // 默认重试3次，每次等待5秒
    }

    public CloudflareInterceptor(int maxRetries, long retryDelay) {
        if (maxRetries < 0 || retryDelay < 0) {
            throw new IllegalArgumentException("Retry count and delay must be non-negative");
        }
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        int retries = 0;
        while (isSafeToRetry(request.getMethod())
                && isCloudflareChallenge(response) && retries < maxRetries) {
            retries++;
            sleep(chain, retryDelay);
            response = chain.proceed(request);
        }

        return response;
    }

    /**
     * 检测是否为 Cloudflare 挑战页面
     */
    private boolean isCloudflareChallenge(Response response) {
        String mitigation = response.getHeader("CF-Mitigated");
        if (mitigation != null && "challenge".equalsIgnoreCase(mitigation.trim())) {
            return true;
        }

        int code = response.getCode();
        if (code != 403 && code != 429 && code != 503) {
            return false;
        }
        String cfRay = response.getHeader("CF-Ray");
        String server = response.getHeader("Server");
        boolean cloudflareResponse = cfRay != null || (server != null
                && server.toLowerCase(Locale.ROOT).contains("cloudflare"));
        if (!cloudflareResponse) {
            return false;
        }
        if (code == 503) {
            return true;
        }

        String body = response.getBody();
        return body != null && (body.contains("Checking your browser")
                || body.contains("Just a moment")
                || body.contains("cf-browser-verification")
                || body.contains("__cf_chl_jschl_tk__"));
    }

    private static boolean isSafeToRetry(String method) {
        return "GET".equals(method) || "HEAD".equals(method)
                || "OPTIONS".equals(method) || "TRACE".equals(method);
    }

    private void sleep(Chain chain, long millis) throws IOException {
        long remaining = millis;
        while (remaining > 0) {
            if (chain.isCanceled()) {
                throw new IOException("Request canceled");
            }
            long chunk = Math.min(remaining, 100L);
            try {
                Thread.sleep(chunk);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting to retry Cloudflare challenge", e);
            }
            remaining -= chunk;
        }
        if (chain.isCanceled()) {
            throw new IOException("Request canceled");
        }
    }
}
