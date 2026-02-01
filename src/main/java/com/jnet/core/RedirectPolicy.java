package com.jnet.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 重定向策略
 * 控制 HTTP 重定向行为
 */
public class RedirectPolicy {
    private final boolean followRedirects;
    private final int maxRedirects;
    private final boolean followCrossDomain;
    private final List<URI> redirectHistory;

    private RedirectPolicy(Builder builder) {
        this.followRedirects = builder.followRedirects;
        this.maxRedirects = builder.maxRedirects;
        this.followCrossDomain = builder.followCrossDomain;
        this.redirectHistory = new ArrayList<>();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * 默认策略：跟随重定向，最多10次
     */
    public static RedirectPolicy defaultPolicy() {
        return newBuilder()
                .followRedirects(true)
                .maxRedirects(10)
                .followCrossDomain(true)
                .build();
    }

    /**
     * 永不重定向
     */
    public static RedirectPolicy never() {
        return newBuilder()
                .followRedirects(false)
                .build();
    }

    /**
     * 严格策略：仅同域重定向
     */
    public static RedirectPolicy sameDomainOnly() {
        return newBuilder()
                .followRedirects(true)
                .followCrossDomain(false)
                .maxRedirects(5)
                .build();
    }

    public boolean shouldFollowRedirects() {
        return followRedirects;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public boolean shouldFollowCrossDomain() {
        return followCrossDomain;
    }

    /**
     * 记录重定向历史
     */
    public void addRedirect(URI uri) {
        redirectHistory.add(uri);
    }

    /**
     * 获取重定向历史（不可变）
     */
    public List<URI> getRedirectHistory() {
        return Collections.unmodifiableList(redirectHistory);
    }

    /**
     * 获取重定向次数
     */
    public int getRedirectCount() {
        return redirectHistory.size();
    }

    /**
     * 检查是否超过最大重定向次数
     */
    public boolean hasExceededMaxRedirects() {
        return redirectHistory.size() >= maxRedirects;
    }

    /**
     * 检查是否应该跟随此重定向
     */
    public boolean shouldFollow(URI from, URI to) {
        if (!followRedirects) {
            return false;
        }

        if (hasExceededMaxRedirects()) {
            return false;
        }

        if (!followCrossDomain) {
            // 检查是否同域
            String fromHost = from.getHost();
            String toHost = to.getHost();
            return fromHost != null && fromHost.equals(toHost);
        }

        return true;
    }

    /**
     * 重置重定向历史
     */
    public void reset() {
        redirectHistory.clear();
    }

    public static class Builder {
        private boolean followRedirects = true;
        private int maxRedirects = 10;
        private boolean followCrossDomain = true;

        public Builder followRedirects(boolean follow) {
            this.followRedirects = follow;
            return this;
        }

        public Builder maxRedirects(int max) {
            if (max < 0) {
                throw new IllegalArgumentException("Max redirects must be non-negative");
            }
            this.maxRedirects = max;
            return this;
        }

        public Builder followCrossDomain(boolean follow) {
            this.followCrossDomain = follow;
            return this;
        }

        public RedirectPolicy build() {
            return new RedirectPolicy(this);
        }
    }

    @Override
    public String toString() {
        return String.format("RedirectPolicy{follow=%s, max=%d, crossDomain=%s, count=%d}",
                followRedirects, maxRedirects, followCrossDomain, redirectHistory.size());
    }
}
