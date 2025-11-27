package com.bytedance.tictok_live.network.websocket;

import android.nfc.Tag;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WenSocket
 */
public class WebSocketManager {
    public static final String TAG = "WebSocketManager";

    // 测试地址
    public static final String WEB_SOCKET_URL = "wss://echo.websocket.org/";
    private static volatile WebSocketManager instance;
    private WebSocket webSocket;
    private OnMessageReceivedListener messageListener;

    public boolean isConnected = false; // 默认未连接

    // 单例模式，确保全局只有一个WebSocket连接
    public static WebSocketManager getInstance() {
        if (instance == null) {
            synchronized (WebSocketManager.class) {
                if (instance == null) {
                    return new WebSocketManager();
                }
            }
        }
        return instance;
    }

    // 初始化WebSocket客户端
    private OkHttpClient getHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    // 连接WebSocket
    public void connect() {
        if (isConnected) {
            Log.d(TAG, "WebSocket已连接，无需重复连接");
            return;
        }

        Request request = new Request.Builder()
                .url(WEB_SOCKET_URL)
                .build();

        // 建立连接
        webSocket = getHttpClient().newWebSocket(request, new WebSocketListener() {
            // 连接成功回调
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                super.onOpen(webSocket, response);
                Log.d(TAG, "WebSocket连接成功");
                isConnected = true;
            }

            // 收到消息回调
            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                super.onMessage(webSocket, text);
                Log.d(TAG, "收到WebSocket消息：" + text);
                // 回调给Activity，更新在线人数
                messageListener.OnMessageReceived(text);
            }

            // 连接关闭回调
            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                super.onClosed(webSocket, code, reason);
                isConnected = false;
            }

            // 连接失败回调
            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                super.onFailure(webSocket, t, response);
                isConnected = false;
                Log.e(TAG, "WebSocket连接失败", t);
            }
        });
    }

    /**
     * 发送消息
     * @param message 消息
     */
    public void sendMessage(String message){
        if (webSocket != null && isConnected){
            webSocket.send(message);
            Log.d(TAG,"WebSocket发送消息：" + message);
        }else {
            Log.d(TAG, "WebSocket未连接,发送消息失败");
        }
    }

    /**
     * 关闭连接
     */
    public void disConnect(){
        if (webSocket != null && isConnected){
            webSocket.close(1000,"关闭连接");
            Log.d(TAG,"WebSocket已关闭");
        }
    }

    /**
     * 设置消息监听（Activity嗲用）
     */
    public void setOnMessageReceivedListener(OnMessageReceivedListener listener){
        this.messageListener = listener;
    }


}
