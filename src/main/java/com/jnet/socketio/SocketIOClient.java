package com.jnet.socketio;

import com.jnet.core.JNet;
import com.jnet.websocket.WebSocketClient;
import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Socket.IO 客户端
 * Phase 6 功能：Engine.IO 协议、事件系统、命名空间、房间
 */
public class SocketIOClient {
    
    private static final int PROTOCOL_VERSION = 4;
    
    private final String url;
    private final Map<String, List<Consumer<Object[]>>> eventListeners = new ConcurrentHashMap<>();
    private String namespace = "/";
    private String sessionId;
    private WebSocketClient wsClient;
    private boolean connected = false;

    public SocketIOClient(String url) {
        this.url = url.replaceFirst("^http", "ws");
    }

    /**
     * 连接到 Socket.IO 服务器
     */
    public void connect() {
        // Phase 6.1: Engine.IO handshake
        performHandshake();
    }

    /**
     * Phase 6.1: Engine.IO 握手
     */
    private void performHandshake() {
        try {
            // 1. HTTP polling handshake
            String handshakeUrl = url.replaceFirst("^ws", "http") + 
                String.format("?EIO=%d&transport=polling", PROTOCOL_VERSION);
            
            String response = JNet.get(handshakeUrl);
            
            // Parse handshake response: 0{"sid":"xxx","upgrades":["websocket"],...}
            if (response.startsWith("0")) {
                JSONObject data = new JSONObject(response.substring(1));
                this.sessionId = data.getString("sid");
                
                // 2. Upgrade to WebSocket
                upgradeToWebSocket();
            }
        } catch (Exception e) {
            triggerEvent("error", new Object[] { e });
        }
    }

    /**
     * Phase 6.1: 升级到 WebSocket
     */
    private void upgradeToWebSocket() {
        String wsUrl = url + String.format("?EIO=%d&transport=websocket&sid=%s", 
            PROTOCOL_VERSION, sessionId);
        
        wsClient = WebSocketClient.newBuilder()
                .listener(new WebSocketClient.WebSocketListener() {
                    @Override
                    public void onOpen(java.net.http.WebSocket webSocket) {
                        connected = true;
                        // Send Engine.IO upgrade packet
                        webSocket.sendText("2probe", true);
                    }

                    @Override
                    public void onMessage(String message) {
                        handleEngineIOMessage(message);
                    }

                    @Override
                    public void onClose(int statusCode, String reason) {
                        connected = false;
                        triggerEvent("disconnect", new Object[] { reason });
                    }

                    @Override
                    public void onError(Throwable error) {
                        triggerEvent("error", new Object[] { error });
                    }
                })
                .build();
        
        wsClient.connect(wsUrl);
    }

    /**
     * 处理 Engine.IO 消息
     */
    private void handleEngineIOMessage(String message) {
        if (message.isEmpty()) return;
        
        char packetType = message.charAt(0);
        String payload = message.length() > 1 ? message.substring(1) : "";
        
        switch (packetType) {
            case '0': // OPEN
                connected = true;
                sendConnectPacket();
                break;
            case '2': // PING
                // Respond with PONG
                if (wsClient != null) {
                    wsClient.sendText("3"); // PONG
                }
                break;
            case '3': // PONG
                // Heartbeat response received
                break;
            case '4': // MESSAGE (Socket.IO packet)
                handleSocketIOMessage(payload);
                break;
            case '5': // UPGRADE
                // Upgrade successful
                break;
            case '6': // NOOP
                break;
        }
    }

    /**
     * 发送 Socket.IO CONNECT 包
     */
    private void sendConnectPacket() {
        String packet = "40" + namespace; // Socket.IO CONNECT
        if (wsClient != null) {
            wsClient.sendText(packet);
        }
        triggerEvent("connect", new Object[0]);
    }

    /**
     * 处理 Socket.IO 消息
     */
    private void handleSocketIOMessage(String payload) {
        if (payload.isEmpty()) return;
        
        char socketType = payload.charAt(0);
        String data = payload.length() > 1 ? payload.substring(1) : "";
        
        switch (socketType) {
            case '0': // CONNECT
                connected = true;
                triggerEvent("connect", new Object[0]);
                break;
            case '1': // DISCONNECT
                connected = false;
                triggerEvent("disconnect", new Object[0]);
                break;
            case '2': // EVENT
                parseAndTriggerEvent(data);
                break;
            case '3': // ACK
                // Handle acknowledgment
                break;
            case '4': // CONNECT_ERROR
                triggerEvent("connect_error", new Object[] { data });
                break;
        }
    }

    /**
     * 解析并触发事件
     */
    private void parseAndTriggerEvent(String data) {
        try {
            JSONArray array = new JSONArray(data);
            if (array.length() > 0) {
                String eventName = array.getString(0);
                
                // Extract event arguments
                Object[] args = new Object[array.length() - 1];
                for (int i = 1; i < array.length(); i++) {
                    args[i - 1] = array.get(i);
                }
                
                triggerEvent(eventName, args);
            }
        } catch (Exception e) {
            triggerEvent("error", new Object[] { e });
        }
    }

    /**
     * Phase 6.2: 监听事件
     */
    public void on(String event, Consumer<Object[]> listener) {
        eventListeners.computeIfAbsent(event, k -> new ArrayList<>())
                .add(listener);
    }

    /**
     * Phase 6.2: 发射事件
     */
    public void emit(String event, Object... args) {
        if (!connected) {
            throw new IllegalStateException("Not connected to Socket.IO server");
        }
        
        try {
            JSONArray payload = new JSONArray();
            payload.put(event);
            for (Object arg : args) {
                payload.put(arg);
            }
            
            String packet = "42" + namespace + payload.toString(); // Socket.IO EVENT
            if (wsClient != null) {
                wsClient.sendText(packet);
            }
        } catch (Exception e) {
            triggerEvent("error", new Object[] { e });
        }
    }

    /**
     * Phase 6.3: 加入房间
     */
    public void join(String room) {
        emit("join", room);
    }

    /**
     * Phase 6.3: 离开房间
     */
    public void leave(String room) {
        emit("leave", room);
    }

    /**
     * Phase 6.3: 切换命名空间
     */
    public SocketIOClient namespace(String namespace) {
        SocketIOClient client = new SocketIOClient(this.url);
        client.namespace = namespace;
        return client;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (wsClient != null) {
            String packet = "41" + namespace; // Socket.IO DISCONNECT
            wsClient.sendText(packet);
            wsClient.close();
        }
        connected = false;
    }

    /**
     * 触发事件监听器
     */
    private void triggerEvent(String event, Object[] args) {
        List<Consumer<Object[]>> listeners = eventListeners.get(event);
        if (listeners != null) {
            for (Consumer<Object[]> listener : listeners) {
                try {
                    listener.accept(args);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 获取会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }
}
