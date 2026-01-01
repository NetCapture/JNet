package com.jnet.core;

import java.io.IOException;
import java.util.List;

/**
 * 测试工具类 - 提供测试辅助方法
 */
public class TestUtils {

    /**
     * 创建一个模拟的拦截器链，用于测试
     * 这个链会在最后一个拦截器调用后返回一个模拟的响应
     */
    public static Interceptor.Chain createMockChain(
            List<Interceptor> interceptors,
            Request request,
            Response mockResponse) {
        return new MockChain(interceptors, 0, request, mockResponse);
    }

    /**
     * Mock Chain 实现 - 用于测试
     */
    static class MockChain implements Interceptor.Chain {
        private final List<Interceptor> interceptors;
        private final int index;
        private final Request request;
        private final Response mockResponse;

        public MockChain(List<Interceptor> interceptors, int index, Request request, Response mockResponse) {
            this.interceptors = interceptors;
            this.index = index;
            this.request = request;
            this.mockResponse = mockResponse;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response proceed(Request request) throws IOException {
            if (index >= interceptors.size()) {
                // 所有拦截器已执行，返回模拟响应
                return mockResponse;
            }
            MockChain next = new MockChain(interceptors, index + 1, request, mockResponse);
            return interceptors.get(index).intercept(next);
        }
    }

    /**
     * 创建一个模拟的响应
     */
    public static Response createMockResponse(Request request, int code, String body) {
        Response.Builder builder = code >= 200 && code < 300
                ? Response.success(request)
                : Response.failure(request);
        return builder.code(code).body(body).duration(10).build();
    }

    /**
     * 创建一个模拟的请求（用于单元测试）
     */
    public static Request createMockRequest(JNetClient client, String url, String method) {
        return client.newGet(url).method(method).build();
    }
}
