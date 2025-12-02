package com.bytedance.tictok_live.network.websocket;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket 管理类（单例）
 * 功能：连接管理、自动重连（指数退避）、心跳检测、消息收发
 */
public class WebSocketManager {
    public static final String TAG = "WebSocketManager";
    public static final String WEB_SOCKET_URL = "wss://echo.websocket.org/";

    // 单例（volatile 保证可见性）
    private static volatile WebSocketManager instance;

    // 核心对象（全局复用 OkHttpClient）
    private final OkHttpClient okHttpClient;
    private WebSocket webSocket;
    private OnMessageReceivedListener messageListener;

    // 状态标记（原子类保证线程安全）
    public final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isWebSocketPaused = new AtomicBoolean(false);
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);

    // 重连配置
    private static final int MAX_RECONNECT_COUNT = 16; // 最大重连次数
    private static final long BASE_RECONNECT_DELAY = 2000; // 基础重连间隔（ms）
    private static final long MAX_RECONNECT_DELAY = 60 * 1000; // 最大重连间隔（60s）
    private final AtomicInteger currentReconnectCount = new AtomicInteger(0);
    private Timer reconnectTimer;

    // 心跳配置
    private static final long HEARTBEAT_INTERVAL = 10000; // 心跳间隔（10秒）
    private static final int MAX_HEARTBEAT_FAIL = 2; // 最大心跳失败次数
    private final AtomicInteger heartbeatFailCount = new AtomicInteger(0);
    private Timer heartbeatTimer;
    private Handler heartbeatDelayHandler; // 用于取消心跳失败检查的延迟任务
    private static final int MSG_HEARTBEAT_FAIL = 1001; // 心跳失败检查消息

    private WebSocketManager() {
        // 全局复用 OkHttpClient
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false) // 关闭OkHttp自带重试，用自定义重连
                .build();

        // 心跳延迟任务的 Handler（独立线程，避免主线程阻塞）
        HandlerThread heartbeatThread = new HandlerThread("WebSocketHeartbeat");
        heartbeatThread.start();
        heartbeatDelayHandler = new Handler(heartbeatThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == MSG_HEARTBEAT_FAIL) {
                    // 心跳失败检查
                    if (isConnected.get() && heartbeatFailCount.get() >= MAX_HEARTBEAT_FAIL) {
                        Log.e(TAG, "心跳失败次数达到上限，触发重连");
                        isConnected.set(false);
                        startReconnect();
                    } else if (isConnected.get()) {
                        int failCount = heartbeatFailCount.incrementAndGet();
                        Log.w(TAG, "心跳未收到响应，失败次数：" + failCount);
                    }
                }
            }
        };
    }

    // 单例模式(双重检查锁)
    public static WebSocketManager getInstance() {
        if (instance == null) {
            synchronized (WebSocketManager.class) {
                if (instance == null) {
                    instance = new WebSocketManager();
                }
            }
        }
        return instance;
    }

    // 连接WebSocket
    public void connect() {
        // 1. 避免重复连接/重连
        if (isConnected.get() || isReconnecting.get()) {
            Log.d(TAG, "WebSocket已连接/正在重连，无需重复连接");
            return;
        }

        // 标记正在重连（原子操作，线程安全）
        isReconnecting.set(true);

        // 2. 构建 WebSocket 请求
        Request request = new Request.Builder()
                .url(WEB_SOCKET_URL)
                .build();

        // 3. 建立连接
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                super.onOpen(webSocket, response);
                Log.d(TAG, "WebSocket连接成功");
                // 重置状态（原子操作）
                isConnected.set(true);
                isReconnecting.set(false);
                currentReconnectCount.set(0);
                heartbeatFailCount.set(0);
                // 停止重连定时器
                stopReconnectTimer();
                // 启动心跳
                startHeartbeat();
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);
                // 暂停中忽略消息
                if (isWebSocketPaused.get()) {
                    Log.d(TAG, "WS已暂停，忽略消息");
                    return;
                }

                Log.d(TAG, "收到WebSocket消息：" + text);

                // 心跳响应：重置失败次数 + 取消未执行的失败检查任务(由于服务器收什么发什么,所以这里需要使用ping来作为心跳响应)
                if ("ping".equals(text)) {
                    heartbeatFailCount.set(0);
                    heartbeatDelayHandler.removeMessages(MSG_HEARTBEAT_FAIL);
                    return;
                }

                // 回调消息（做空指针判断）
                if (messageListener != null) {
                    // 确保回调在主线程（避免子线程更新UI）
                    new Handler(Looper.getMainLooper()).post(() -> {
                        messageListener.onMessageReceived(text);
                    });
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                super.onClosed(webSocket, code, reason);
                Log.d(TAG, "WebSocket连接关闭：code=" + code + ", reason=" + reason);
                // 重置状态
                isConnected.set(false);
                isReconnecting.set(false);
                stopHeartbeat();
                // 关闭连接时取消OkHttp的请求
                okHttpClient.dispatcher().cancelAll();
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                super.onFailure(webSocket, t, response);
                Log.e(TAG, "WebSocket连接失败", t);
                // 重置状态
                isConnected.set(false);
                isReconnecting.set(false);
                stopHeartbeat();
                // 触发重连
                startReconnect();
                // 关闭连接时取消OkHttp的请求
                okHttpClient.dispatcher().cancelAll();
            }
        });
    }

    /**
     * 重连（指数退避，线程安全）
     */
    private void startReconnect() {
        // 检查重连次数上限
        int currentCount = currentReconnectCount.get();
        if (currentCount >= MAX_RECONNECT_COUNT) {
            Log.e(TAG, "WS重连次数达到上限（" + MAX_RECONNECT_COUNT + "），停止重连");
            stopReconnectTimer();
            isReconnecting.set(false);
            return;
        }

        // 停止旧的重连定时器
        stopReconnectTimer();

        // 计算重连间隔（指数退避 + 最大间隔限制）
        long reconnectDelay = BASE_RECONNECT_DELAY * (1 << currentCount);
        reconnectDelay = Math.min(reconnectDelay, MAX_RECONNECT_DELAY);
        // 重连次数+1（原子操作）
        currentReconnectCount.incrementAndGet();

        Log.d(TAG, "WS准备重连，第" + currentReconnectCount.get() + "次，间隔：" + reconnectDelay + "ms");
        reconnectTimer = new Timer("WebSocketReconnectTimer");
        reconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // 切回主线程执行连接（OkHttp的WebSocket操作建议在主线程）
                new Handler(Looper.getMainLooper()).post(() -> {
                    connect();
                });
            }
        }, reconnectDelay);
    }

    /**
     * 停止重连定时器
     */
    private void stopReconnectTimer() {
        if (reconnectTimer != null) {
            reconnectTimer.cancel();
            reconnectTimer.purge(); // 清理未执行的任务
            reconnectTimer = null;
        }
    }

    /**
     * 启动心跳机制（独立线程执行，避免主线程阻塞）
     */
    private void startHeartbeat() {
        // 停止旧心跳
        stopHeartbeat();

        Log.d(TAG, "启动心跳机制，间隔：" + HEARTBEAT_INTERVAL + "ms");
        heartbeatTimer = new Timer("WebSocketHeartbeatTimer");
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 心跳发送逻辑（非主线程）
                if (webSocket != null && isConnected.get()) {
                    try {
                        // 发送ping（OkHttp的WebSocket.send是线程安全的）
                        webSocket.send("ping");
                        Log.d(TAG, "发送心跳：ping");

                        // 发送后延迟检查是否收到pong（避免叠加延迟任务，先移除旧的）
                        heartbeatDelayHandler.removeMessages(MSG_HEARTBEAT_FAIL);
                        Message msg = heartbeatDelayHandler.obtainMessage(MSG_HEARTBEAT_FAIL);
                        heartbeatDelayHandler.sendMessageDelayed(msg, HEARTBEAT_INTERVAL);
                    } catch (Exception e) {
                        Log.e(TAG, "发送心跳失败", e);
                        heartbeatFailCount.incrementAndGet();
                    }
                } else {
                    stopHeartbeat();
                }
            }
        }, 0, HEARTBEAT_INTERVAL);
    }

    /**
     * 停止心跳机制
     */
    private void stopHeartbeat() {
        Log.d(TAG, "停止心跳机制");
        // 停止定时器
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer.purge();
            heartbeatTimer = null;
        }
        // 取消未执行的失败检查任务
        heartbeatDelayHandler.removeMessages(MSG_HEARTBEAT_FAIL);
        // 重置失败次数
        heartbeatFailCount.set(0);
    }

    /**
     * 发送消息（线程安全）
     * @param message 消息内容
     */
    public void sendMessage(String message) {
        if (message == null || message.isEmpty()) {
            Log.w(TAG, "发送消息为空，忽略");
            return;
        }
        if (webSocket != null && isConnected.get()) {
            // 在非主线程发送消息
            new Thread(() -> {
                try {
                    webSocket.send(message);
                    Log.d(TAG, "WebSocket发送消息：" + message);
                } catch (Exception e) {
                    Log.e(TAG, "发送消息失败", e);
                }
            }).start();
        } else {
            Log.d(TAG, "WebSocket未连接，发送消息失败");
        }
    }

    /**
     * 关闭连接（完整清理资源）
     */
    public void disconnect() {
        Log.d(TAG, "主动关闭WebSocket连接");
        // 停止所有定时器
        stopReconnectTimer();
        stopHeartbeat();
        // 重置状态
        isConnected.set(false);
        isReconnecting.set(false);
        currentReconnectCount.set(0);
        // 关闭WebSocket
        if (webSocket != null) {
            webSocket.close(1000, "主动关闭连接");
            webSocket = null;
        }
        // 取消OkHttp的所有请求
        okHttpClient.dispatcher().cancelAll();
    }

    /**
     * 设置消息监听
     * @param listener 消息监听回调
     */
    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        this.messageListener = listener;
    }

    /**
     * 暂停 WebSocket 消息接收（不断开连接）
     */
    public void pauseMessageReceive() {
        isWebSocketPaused.set(true);
        Log.d(TAG, "暂停WS消息接收");
    }

    /**
     * 恢复 WebSocket 消息接收
     */
    public void resumeMessageReceive() {
        isWebSocketPaused.set(false);
        Log.d(TAG, "恢复WS消息接收");
    }

    /**
     * 释放资源（建议在Application退出时调用）
     */
    public void release() {
        disconnect();
        // 释放HandlerThread
        if (heartbeatDelayHandler != null) {
            heartbeatDelayHandler.getLooper().quitSafely();
            heartbeatDelayHandler = null;
        }
        messageListener = null;
        instance = null; // 单例置空
    }

    /**
     * 消息接收监听
     */
    public interface OnMessageReceivedListener {
        void onMessageReceived(String message);
    }
}