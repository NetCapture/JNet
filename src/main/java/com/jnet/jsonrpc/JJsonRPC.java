package com.jnet.jsonrpc;

import com.jnet.core.JNet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JSON-RPC 2.0 Client Facade
 * Provides a simple API for making JSON-RPC calls.
 */
public final class JJsonRPC {

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

    private JJsonRPC() {}

    /**
     * Call a remote method with positional parameters
     */
    public static String call(String url, String method, List<Object> params) {
        return execute(url, method, params);
    }

    /**
     * Call a remote method with named parameters
     */
    public static String call(String url, String method, Map<String, Object> params) {
        return execute(url, method, params);
    }

    /**
     * Call a remote method without parameters
     */
    public static String call(String url, String method) {
        return execute(url, method, null);
    }

    private static String execute(String url, String method, Object params) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        payload.put("id", ID_GENERATOR.getAndIncrement());

        if (params != null) {
            payload.put("params", params);
        }

        return JNet.postJson(url, payload);
    }

    /**
     * Send a notification (no id, no response expected)
     */
    public static void notify(String url, String method, Object params) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
        if (params != null) {
            payload.put("params", params);
        }
        // Notification does not have "id"
        JNet.postJsonAsync(url, payload);
    }
}
