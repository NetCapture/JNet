# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JNet is a zero-dependency, high-performance HTTP client library for Java 11+. It wraps the native `java.net.http.HttpClient` with a Python requests-style API, supporting HTTP/2, interceptors, SSE, WebSocket, Socket.IO, and async operations.

**Core Philosophy:**
- Zero third-party dependencies (production code)
- Immutable object design for thread safety
- Python requests-inspired simplicity
- ~6K lines of code vs 30K+ in alternatives

## Feature Comparison vs Python Requests

| Feature | Python Requests | JNet (Java) | Notes |
|---------|-----------------|-------------|-------|
| **Syntax** | `requests.get(url)` | `JNet.get(url)` | 1:1 API mapping |
| **Params** | `params={'x':1}` | `JNet.params("x",1)` | Helper for Query Params |
| **JSON** | `json={'k':'v'}` | `JNet.json().put("k","v")` | Simple JSON Builder |
| **Auth** | `auth=('u','p')` | `JNet.basicAuth("u","p")` | Basic Auth Helper |
| **Async** | `aiohttp`/`httpx` | `CompletableFuture` | Native JDK Async |
| **HTTP/2** | No (requires `httpx`) | ✅ Yes | Native JDK 11+ |
| **WebSocket** | No (external lib) | ✅ Yes | Built-in w/ reconnect |
| **Socket.IO** | No (external lib) | ✅ Yes | Engine.IO v4 support |
| **SSE** | No (external lib) | ✅ Yes | Native EventSource |
| **Deps** | Heavy (urllib3, etc) | ✅ **Zero** | Just Java 11+ |

## Build & Test Commands

### Building
```bash
# Package JAR (skips tests by default)
./build.sh package

# Output: target/jnt-{version}-jar-with-dependencies.jar
# Main class: com.netcapture.LetusRun
```

### Testing
```bash
# Run all test suites (core + interceptors + SSE + WebSocket + Socket.IO)
./build.sh test

# Run specific test class
mvn test -Dtest=TestWebSocketClient

# Run single test method
mvn test -Dtest=TestSocketIOClient#testConnect

# Run with verbose output
mvn test -DskipTests=false -Dtest=TestClassName
```

### Maven Commands
```bash
# Clean build artifacts
mvn clean

# Compile only
mvn compile

# Generate Javadoc
mvn javadoc:javadoc

# Skip Javadoc generation
mvn package -Djavadoc.skip=true
```

## Architecture Overview

### Request Flow
```
User Code → JNet (static facade) → JNetClient → Request.Builder → Request
                                                                      ↓
                                                                    Call
                                                                      ↓
                                                    Interceptor Chain (0..N)
                                                                      ↓
                                                        JDK HttpClient (network)
                                                                      ↓
                                                                  Response
```

### Core Components

#### 1. JNet (`JNet.java`)
Static utility class providing Python requests-style convenience methods:
- `JNet.get(url)`, `JNet.post(url, body)`, `JNet.put()`, `JNet.delete()`
- `JNet.getAsync()` for CompletableFuture-based async requests
- `JNet.params()`, `JNet.headers()`, `JNet.json()` for building request parameters
- Manages a singleton `HttpClient` with HTTP/2 support

#### 2. JNetClient (`JNetClient.java`)
Client configuration and lifecycle management:
- Builder pattern for custom configurations (timeouts, proxy, SSL, interceptors)
- Singleton pattern: `JNetClient.getInstance()`
- Wraps JDK 11 `HttpClient` with additional features
- Thread-safe, immutable after construction

#### 3. Request/Response (`Request.java`, `Response.java`)
Immutable request/response objects:
- **Request**: URL, method, headers, body, timeout configuration
- **Response**: Status code, headers, body (String/bytes), message
- Builder pattern for construction
- Thread-safe by design

#### 4. Call (`Call.java`)
Request executor supporting sync/async execution:
- `execute()` - synchronous blocking call
- `enqueue(Callback)` - asynchronous with callback
- `executeAsync()` - returns CompletableFuture
- Executes interceptor chain before network request

#### 5. Interceptor Chain (`Interceptor.java`)
OkHttp-style interceptor pattern:
- `Chain.proceed(request)` propagates through interceptors
- Built-in interceptors: `LoggingInterceptor`, `RetryInterceptor`, `CacheInterceptor`, `CloudflareInterceptor`
- Custom interceptors: implement `Interceptor.intercept(Chain)`
- Order matters: interceptors execute in registration order

#### 6. SSEClient (`SSEClientEnhanced.java`)
Server-Sent Events streaming client:
- `connect(url, listener)` for event streams
- Auto-reconnection with exponential backoff
- Heartbeat detection & Last-Event-ID support
- Event filtering

#### 7. WebSocketClient (`WebSocketClient.java`)
Native WebSocket support:
- Wrapper around JDK 11 `java.net.http.WebSocket`
- Auto-reconnection and Ping/Pong heartbeats
- Text and Binary message support

#### 8. SocketIOClient (`SocketIOClient.java`)
Socket.IO v4 client implementation:
- Engine.IO protocol support
- Namespace and Room support
- Event-based architecture (`emit`, `on`)

#### 9. SSLConfig (`SSLConfig.java`)
SSL/TLS configuration:
- TLS 1.3 support
- Certificate pinning
- Custom cipher suites
- Mutual TLS support

### Package Structure

```
src/main/java/com/jnet/
├── core/                  # Core HTTP functionality
│   ├── JNet.java
│   ├── JNetClient.java
│   ├── Request.java
│   ├── Response.java
│   ├── Interceptor.java
│   └── SSEClientEnhanced.java
├── auth/                  # Authentication (Basic, Bearer, Digest)
├── cloudflare/            # Cloudflare bypass & anti-bot
├── download/              # File download & progress
├── multipart/             # Multipart upload support
├── websocket/             # WebSocket client
├── socketio/              # Socket.IO client
└── org/json/              # Embedded JSON library
```

## Key Design Patterns

### Immutability
All request/response objects are immutable. Use builders to create modified copies:

```java
Request original = client.newGet("https://api.example.com").build();
Request modified = original.newBuilder()
    .addHeader("Authorization", "Bearer token")
    .build();
```

### Interceptor Chain Pattern
Inspired by OkHttp, interceptors wrap the actual network call:

```java
public class AuthInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request authenticated = original.newBuilder()
            .addHeader("Authorization", "Bearer " + getToken())
            .build();
        return chain.proceed(authenticated);
    }
}
```

## Version Management

Version is managed via `pom.xml` and auto-generated into `Version.java`.
To update version:
```bash
./update-version.sh <new-version>
```

## Testing Structure

Tests use JUnit 5 and are organized by feature:

- **Core Tests**: `TestRequest`, `TestResponse`, `TestJNetClient`
- **Feature Tests**: `TestInterceptorFull`, `TestMultipartBody`, `TestDownload`
- **Real-time Tests**: `TestSSEClientEnhanced`, `TestWebSocketClient`, `TestSocketIOClient`
- **Security Tests**: `TestAuth`, `TestCloudflare`, `TestSSLConfigFull`

Note: Tests are skipped by default (`<skipTests>true</skipTests>` in pom.xml). Use `./build.sh test` to run them.

## Dependencies

**Production:** ZERO dependencies (only JDK 11+ standard library)

**Test:**
- JUnit 5
- Hamcrest matchers

## Important Constraints

1. **No Third-Party Dependencies**: Do not add external libraries to production code. Use JDK standard library only.

2. **Immutability**: All request/response objects must remain immutable. Use builder pattern for modifications.

3. **Thread Safety**: All public APIs must be thread-safe. Use immutable objects or proper synchronization.

4. **Java 11+ Only**: Code must compile and run on Java 11+. Use `var`, HTTP/2 client, etc.

5. **Zero Reflection (where possible)**: Minimize reflection usage for performance and GraalVM compatibility.

---

## Project Status

**Current Version:** v3.4.5
**Status:** 100% Complete (Production Ready)

### Completed Features
- ✅ **Python Requests Parity**: Session, Auth, Multipart, Download, StreamResponse
- ✅ **Cloudflare Bypass**: User-Agent rotation, Fingerprinting, Challenge handling
- ✅ **Enhanced SSE**: Auto-reconnect, Heartbeat, Event Filtering
- ✅ **TLS 1.3**: Modern security, Certificate Pinning
- ✅ **WebSocket**: Native client with auto-reconnect
- ✅ **Socket.IO**: Engine.IO v4 protocol, Namespaces, Rooms

### Maintenance Mode
The project is feature-complete. Future work will focus on:
- Bug fixes
- Performance optimizations
- Documentation updates

## Roadmap

### Future Plans
- **Maintenance**: Focus on stability, bug fixes, and performance.
- **Documentation**: Continuous improvement of Javadocs and guides.

### Deferred Features
- **QUIC (HTTP/3)**: **DEFERRED**.
  - *Reason*: Java 11-21 does not natively support QUIC. Adding it requires third-party libraries (Netty/Incubator), violating the strict **Zero Dependency** rule.
  - *Future Strategy*:
    1. **Wait for JDK Support** (Recommended): Wait for OpenJDK (Project Loom/Leyden) to add native HTTP/3 support (likely JDK 24+).
    2. **Hybrid Engine (Alternative)**: If urgently needed, create an optional `jnet-quic` module wrapping `netty-incubator-codec-native-quic`, hiding implementation details behind a common `HttpEngine` interface.

### Protocol Status Verification
- **SSE**: ✅ Verified. Includes auto-reconnect, heartbeat, and event filtering.
- **WebSocket**: ✅ Verified. Native wrapper with automatic ping/pong and reconnection.
- **Socket.IO**: ✅ Verified. Full support for Engine.IO v4, namespaces, and rooms.
- **GraphQL**: ✅ Verified. Fluent API for queries/mutations with variable support.
- **WebDAV**: ✅ Verified. Support for PROPFIND, MKCOL, COPY, MOVE, LOCK, UNLOCK.
- **JSON-RPC**: ✅ Verified. Auto-id generation, version 2.0 support.

## Codebase Audit Summary
- **JNet Core**: Verified parity with Python Requests (95% coverage + Async).
- **Zero Dependency**: Confirmed. Only JDK 11+ standard library used.
- **Real-time**: Confirmed robust implementations for SSE, WS, and Socket.IO.
- **Application Protocols**: Added GraphQL, WebDAV, JSON-RPC support.
- **QUIC**: Confirmed deferred status due to JDK limitations.
