# JNet - Zero-Dependency High-Performance HTTP Client

[![Maven Central](https://img.shields.io/maven-central/v/com.netcapture/jnt.svg)](https://maven.pkg.github.com/NetCapture/JNet)
[![Java](https://img.shields.io/badge/Java-11+-blue.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![CI](https://github.com/NetCapture/JNet/actions/workflows/ci.yml/badge.svg)](https://github.com/NetCapture/JNet/actions)

> 🚀 A minimalist HTTP client based on JDK 11+ HttpClient, with zero third-party dependencies and a Python requests-style API.

## ✨ Core Features

- ✅ **Zero Dependencies** - Uses only JDK 11+ standard library
- ✅ **HTTP/2 Support** - Native support via `java.net.http.HttpClient`
- ✅ **Python-style API** - Simple, intuitive, blocking/async API
- ✅ **Real-Time Web** - **WebSocket**, **Socket.IO**, and **SSE** (Server-Sent Events) support
- ✅ **Anti-Bot Bypass** - Cloudflare challenge handling, browser fingerprinting, UA rotation
- ✅ **Advanced Security** - **TLS 1.3**, Certificate Pinning, Custom Cipher Suites
- ✅ **Thread Safe** - Immutable object design

## 📦 Installation

### Maven
```xml
<dependency>
    <groupId>com.netcapture</groupId>
    <artifactId>jnt</artifactId>
    <version>3.5.1</version>
</dependency>
```

### Gradle
```groovy
implementation 'com.netcapture:jnt:3.5.1'
```

**Requirement: Java 11+**

## 🚀 Quick Start

### Basic Requests
```java
// GET
String data = JNet.get("https://api.example.com/data");

// POST JSON
String result = JNet.post("https://api.example.com/users",
    JNet.json().put("name", "Alice").put("age", 25));

// With Params and Headers
String data = JNet.get("https://api.example.com/search",
    JNet.params("q", "java"),
    JNet.headers("Authorization", "Bearer token"));
```

### WebSocket Client
```java
WebSocketClient client = WebSocketClient.newBuilder()
    .listener(new WebSocketListener() {
        @Override
        public void onOpen(WebSocket ws) {
            System.out.println("Connected!");
            ws.sendText("Hello", true);
        }
        @Override
        public void onMessage(String message) {
            System.out.println("Received: " + message);
        }
    })
    .build();

client.connect("wss://echo.websocket.org/");
```

### Socket.IO Client
```java
SocketIOClient socket = new SocketIOClient("http://localhost:3000");

socket.on("connect", args -> {
    System.out.println("Connected to Socket.IO server");
    socket.emit("join", "room1");
});

socket.on("message", args -> {
    System.out.println("Message: " + args[0]);
});

socket.connect();
```

### Cloudflare Bypass
```java
JNetClient client = JNetClient.newBuilder()
    .addInterceptor(new CloudflareInterceptor()) // Handles challenges
    .addInterceptor(new RequestTimingInterceptor(500, 2000)) // Human-like delays
    .build();

Request request = client.newGet("https://protected-site.com")
    .header("User-Agent", new UserAgentRotator().getRandomUserAgent())
    .build();
```

### File Upload (Multipart)
```java
MultipartBody body = MultipartBody.newBuilder()
    .addFormField("type", "avatar")
    .addFilePart("image", new File("user.jpg"))
    .build();

JNet.post("https://api.example.com/upload", body);
```

### SSE (Server-Sent Events)
```java
SSEClientEnhanced sse = new SSEClientEnhanced();
sse.connect("https://api.example.com/events", new EnhancedSSEListener() {
    @Override
    public void onEvent(SSEEvent event) {
        System.out.println("Event: " + event.getData());
    }

    @Override
    public void onReconnect(int attempt) {
        System.out.println("Reconnecting... " + attempt);
    }
});
```

### SSL/TLS Configuration
```java
// Enable TLS 1.3 only with Certificate Pinning
SSLConfig ssl = new SSLConfig.Builder()
    .protocols("TLSv1.3")
    .pinCertificate("sha256/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    .build();

JNetClient client = JNetClient.newBuilder()
    .sslConfig(ssl)
    .build();
```

## 🏗️ Architecture

```
User Code → JNet (Static Facade)
               ↓
          JNetClient (Configuration)
               ↓
     Request → Call → Interceptors → JDK HttpClient
                                          ↓
                                     Response
```

**Modules:**
- `com.jnet.core`: Core HTTP client, Request/Response, Interceptors
- `com.jnet.websocket`: Native WebSocket client
- `com.jnet.socketio`: Socket.IO v4 client
- `com.jnet.auth`: Authentication (Basic, Bearer, Digest)
- `com.jnet.cloudflare`: Anti-bot bypass tools

## 🧪 Testing

```bash
# Run all tests
./build.sh test

# Package JAR
./build.sh package
```

**Coverage:**
- ✅ Core HTTP & Interceptors
- ✅ WebSocket & Socket.IO
- ✅ SSE (Enhanced)
- ✅ Authentication & Security

## 📊 Performance Comparison

| Feature | JNet | OkHttp | Apache HttpClient |
|---------|------|--------|-------------------|
| **Dependencies** | **0** | 3+ | 5+ |
| **Code Size** | **~6K Lines** | ~30K Lines | ~50K Lines |
| **HTTP/2** | ✅ Native | ✅ | ⚠️ (Complex) |
| **Socket.IO** | ✅ | ❌ | ❌ |
| **WebSocket** | ✅ | ✅ | ✅ |
| **SSE** | ✅ | ❌ (Requires lib) | ❌ |
| **Cloudflare Bypass** | ✅ | ❌ | ❌ |
| **Thread Safe** | ✅ | ✅ | ✅ |

## 📁 Project Structure

```
src/main/java/com/jnet/
├── core/              # Core functionality
├── websocket/         # WebSocket client
├── socketio/          # Socket.IO client
├── auth/              # Auth providers
├── cloudflare/        # Anti-bot bypass
├── multipart/         # Multipart uploads
└── download/          # File downloads
```

## 📄 License

Apache 2.0 - See [LICENSE](LICENSE)

---

**JNet** © 2020-2026 NetCapture Group
