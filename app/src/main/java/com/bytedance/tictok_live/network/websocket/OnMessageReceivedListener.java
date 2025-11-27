package com.bytedance.tictok_live.network.websocket;

/**
 * 消息回调接口，让Activity监听消息
 */
public interface OnMessageReceivedListener {
    void OnMessageReceived(String message);
}
