package com.jnet.core;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Deprecated compatibility entry points for examples that were historically
 * packaged in the runtime JAR. New examples belong in documentation or tests.
 *
 * @deprecated Use {@link JNet} and {@link JNetClient} directly.
 */
@Deprecated
public class Examples {
    public Examples() {
    }

    public static void basicGetExample() throws Exception {
        JNet.get("https://httpbin.org/get");
    }

    public static void postJsonExample() throws Exception {
        JNet.postJson("https://httpbin.org/post", Collections.singletonMap("name", "JNet"));
    }

    public static void formDataExample() throws Exception {
        JNet.post("https://httpbin.org/post", "username=admin&remember=true",
                Collections.singletonMap("Content-Type", "application/x-www-form-urlencoded"));
    }

    public static void headersExample() throws Exception {
        JNet.get("https://httpbin.org/headers",
                Collections.singletonMap("Accept", "application/json"),
                (java.util.Map<String, String>) null);
    }

    public static void customClientExample() throws Exception {
        JNetClient client = JNetClient.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        client.newGet("https://httpbin.org/get").build().newCall().execute();
    }

    public static void asyncExample() throws Exception {
        JNet.getAsync("https://httpbin.org/delay/1").get(5, TimeUnit.SECONDS);
    }

    public static void errorHandlingExample() throws Exception {
        JNet.get("https://httpbin.org/status/404");
    }

    public static void utilsExample() {
        JNetUtils.encodeBase64("Hello, JNet!");
        JNetUtils.urlEncode("Hello world");
    }

    public static void main(String[] args) {
        utilsExample();
    }
}
