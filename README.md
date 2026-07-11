# JNet

[![Maven Central](https://img.shields.io/maven-central/v/com.netcapture/jnt.svg)](https://central.sonatype.com/artifact/com.netcapture/jnt)
[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-Apache--2.0-green.svg)](LICENSE)
[![CI](https://github.com/NetCapture/JNet/actions/workflows/ci.yml/badge.svg)](https://github.com/NetCapture/JNet/actions/workflows/ci.yml)

JNet is a compact networking SDK implemented with the JDK standard library.
Consumer applications get **zero third-party runtime dependencies**.

> **Platform boundary:** JNet 3.x requires JDK 11 or newer and targets desktop/server JVMs.
> It does **not** run natively on Android because its public API uses `java.net.http`.
> Android support requires the planned transport split in the next major version; changing only the Maven compiler level is not sufficient.

## Installation

```xml
<dependency>
  <groupId>com.netcapture</groupId>
  <artifactId>jnt</artifactId>
  <version>3.5.2</version>
</dependency>
```

```groovy
implementation 'com.netcapture:jnt:3.5.2'
```

JUnit and JaCoCo are used only while building/testing the project; they are not runtime dependencies of the published JAR.

## HTTP

Static convenience API:

```java
String body = JNet.get("https://api.example.com/items");

String created = JNet.postJson(
    "https://api.example.com/items",
    JNet.json().put("name", "example"));
```

Request/response API:

```java
JNetClient client = JNetClient.newBuilder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build();

Response response = client.newPost("https://api.example.com/items")
    .header("Content-Type", "application/json")
    .body("{\"name\":\"example\"}")
    .build()
    .newCall()
    .execute();
```

Custom HTTP tokens such as WebDAV's `PROPFIND`, `MKCOL`, `COPY`, and `MOVE` are supported.
Method tokens are validated and retain their original case.

`Request`/`Call` follows at most 10 redirects, rejects HTTPS downgrade, and removes
credentials and explicit cookies when the origin changes. Clients are stateless by
default; opt into a cookie session explicitly:

```java
CookieManager cookies = new CookieManager();
JNetClient session = JNetClient.newBuilder()
    .cookieHandler(cookies)
    .build();
```

The raw client returned by `getHttpClient()` deliberately uses
`HttpClient.Redirect.NEVER`; use `Request`/`Call` when safe redirect handling is required.

## Multipart upload

```java
MultipartBody multipart = MultipartBody.newBuilder()
    .addFormField("type", "avatar")
    .addFilePart("image", new File("user.jpg"), "image/jpeg")
    .build();

Response response = JNetClient.create()
    .newPost("https://api.example.com/upload")
    .header("Content-Type", multipart.getContentType())
    .body(multipart.toBodyPublisher())
    .build()
    .newCall()
    .execute();
```

Multipart file bodies are streamed and expose an exact content length when all parts have known sizes.

## SSE

```java
SSEClientEnhanced sse = SSEClientEnhanced.newBuilder()
    .maxRetries(5)
    .build();

sse.connect("https://api.example.com/events", Collections.emptyMap(),
    new SSEClientEnhanced.EnhancedSSEListener() {
      public void onEvent(SSEClientEnhanced.SSEEvent event) {
        System.out.println(event.getData());
      }

      public void onReconnect(int attempt) {
        System.out.println("reconnect " + attempt);
      }

      public void onError(Exception error) {
        error.printStackTrace();
      }
    });

// Later:
sse.close();
```

Default SSE transports do not automatically follow `3xx` responses because stream
headers often contain credentials; configure the final event endpoint explicitly.

## WebSocket and Socket.IO

```java
WebSocketClient webSocket = WebSocketClient.newBuilder()
    .listener(new WebSocketClient.WebSocketListener() {
      public void onMessage(String message) {
        System.out.println(message);
      }
    })
    .build();

webSocket.connect("wss://example.com/socket");
```

```java
SocketIOClient socket = new SocketIOClient("https://example.com");
socket.on("connect", args -> socket.emit("join", "room-1"));
socket.on("message", args -> System.out.println(args[0]));
socket.connect();
```

The Socket.IO client implements Engine.IO v4 WebSocket upgrade and text events. Binary attachments, acknowledgements, and polling-only servers are intentionally outside its current small scope.

## Other protocols

- TCP client/session and a bounded-worker HTTP server over `ServerSocket`
- UDP client and a single-threaded datagram server; handlers must remain non-blocking
- RTSP 1.0 control sessions with persistent TCP/TLS, Basic/Digest authentication, SDP parsing, and interleaved-frame skipping
- HLS playlist parsing and binary segment download
- WebDAV, GraphQL, and JSON-RPC convenience facades

Protocol-specific classes retain narrow boundaries. For example, the RTSP client controls sessions but does not decode or expose RTP media frames.

## Build and verification

```bash
./build.sh test
./build.sh verify
./build.sh package
```

`verify` is the blocking CI gate. Network-dependent and stress tests are intentionally
excluded from that deterministic gate and should be run explicitly in an environment
that provides their required endpoints and capacity.

## Project layout

```text
src/main/java/com/jnet/
├── core/        HTTP, request/response, cache, SSE, utilities
├── auth/        Basic, Bearer, Digest
├── multipart/   streaming multipart bodies
├── websocket/   JDK WebSocket client
├── socketio/    Engine.IO v4 / Socket.IO text client
├── tcp/         TCP client, session, small server
├── udp/         UDP client and server
├── rtsp/        RTSP control and SDP
├── hls/         HLS playlists and segments
├── webdav/      WebDAV convenience facade
├── graphql/     GraphQL convenience facade
└── jsonrpc/     JSON-RPC convenience facade
```

## License

Apache-2.0. See [`LICENSE`](LICENSE).
