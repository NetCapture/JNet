package com.jnet.core;

import com.jnet.cloudflare.RequestTimingInterceptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class TestOptimizationChanges {

    @Test
    void testResponseCacheVaryHeaders() {
        ResponseCache cache = new ResponseCache(60000);
        JNetClient client = JNetClient.getInstance();
        Request reqA = client.newGet("https://example.com/api").header("Authorization", "Bearer A").build();
        Request reqB = client.newGet("https://example.com/api").header("Authorization", "Bearer B").build();
        Response resp = Response.success(reqA).code(200).body("a").build();

        cache.put(reqA, resp);
        assertNotNull(cache.get(reqA));
        assertNull(cache.get(reqB));
    }

    @Test
    void testResponseHeaderValues() {
        Request req = JNetClient.getInstance().newGet("https://example.com").build();
        Response resp = Response.success(req)
                .code(200)
                .headerValues("Set-Cookie", Arrays.asList("a=1", "b=2"))
                .build();

        assertEquals("a=1", resp.getHeader("Set-Cookie"));
        assertEquals("a=1", resp.getHeader("set-cookie"));
        assertEquals(2, resp.getHeaderValues("Set-Cookie").size());
        assertEquals(2, resp.getHeaderValues("set-cookie").size());
    }

    @Test
    void testClientInterceptorsConfigurable() {
        Interceptor interceptor = chain -> Response.success(chain.request()).code(200).body("ok").build();
        JNetClient client = JNetClient.newBuilder().addInterceptor(interceptor).build();
        assertEquals(1, client.getInterceptors().size());
    }

    @Test
    void testRequestTimingDelayWithinRange() throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(10, 20);
        Method method = RequestTimingInterceptor.class.getDeclaredMethod("calculateDelay");
        method.setAccessible(true);
        for (int i = 0; i < 100; i++) {
            long delay = (long) method.invoke(interceptor);
            assertTrue(delay >= 10 && delay <= 20);
        }
    }

    @Test
    void testRequestHeaderLookupIsCaseInsensitive() {
        Request request = JNetClient.getInstance()
                .newGet("https://example.com")
                .header("Content-Type", "application/json")
                .build();

        assertEquals("application/json", request.getHeader("content-type"));
    }

    @Test
    void testRequestRejectsRelativeUrls() {
        assertThrows(IllegalArgumentException.class, () ->
                JNetClient.getInstance().newGet("not-a-url").build());
    }

    @Test
    void testRequestAcceptsBarePercentInUrl() {
        Request request = JNetClient.getInstance()
                .newGet("https://example.com/test?q=hello%20world&special=!@$%")
                .build();

        assertTrue(request.getUrlString().contains("%25"));
    }
}
