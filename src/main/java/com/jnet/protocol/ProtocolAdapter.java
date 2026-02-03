package com.jnet.protocol;

import java.io.IOException;

/**
 * Protocol Adapter Interface
 * Base interface for building custom protocols on top of TCP/UDP
 *
 * @author sanbo
 * @version 3.5.0
 */
public interface ProtocolAdapter {

    /**
     * Execute a protocol request
     */
    ProtocolResponse execute(ProtocolRequest request) throws IOException;

    /**
     * Execute asynchronously
     */
    java.util.concurrent.CompletableFuture<ProtocolResponse> executeAsync(ProtocolRequest request);

    /**
     * Check if protocol supports streaming
     */
    default boolean supportsStreaming() {
        return false;
    }
}
