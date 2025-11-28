package com.bytedance.tictok_live.repository;


import com.bytedance.tictok_live.content.BusinessConstant;
import com.bytedance.tictok_live.model.Comment;
import com.bytedance.tictok_live.model.HostInfo;
import com.bytedance.tictok_live.network.retrofit.HostApiService;
import com.bytedance.tictok_live.network.retrofit.RetrofitClient;
import com.bytedance.tictok_live.network.websocket.OnMessageReceivedListener;
import com.bytedance.tictok_live.network.websocket.WebSocketManager;

import java.util.List;

import retrofit2.Callback;

/**
 * 直播数据仓库
 */
public class LiveRepository {
    // 网络、WebSocket实例
    private HostApiService hostApiService;
    private WebSocketManager webSocketManager;


    // 初始化
    public LiveRepository(){
        hostApiService = RetrofitClient.createApi(HostApiService.class);
        webSocketManager = WebSocketManager.getInstance();
    }

    // 获取主播信息
    public void getHostInfo(Callback<HostInfo> callback){
        hostApiService.getHostInfo().enqueue(callback);
    }

    // 获取公屏评论
    public void getInitComments(Callback<List<Comment>> callback){
        hostApiService.getComments().enqueue(callback);
    }

    // 发送评论
    public void sendComment(String content, Callback<Comment> callback){
        hostApiService.sendComment(content).enqueue(callback);
    }

    // Websocket 消息监听
    public void observeWebSocketMessage(OnMessageReceivedListener listener){
        webSocketManager.setOnMessageReceivedListener(listener);
    }

    /**
     * 发送在线人数加1的消息
     * @return true：发送成功（WS 已连接）；false：发送失败（WS 未连接）
     */
    public boolean sendOnlineCountIncreaseMsg(){
        if (webSocketManager != null && webSocketManager.isConnected){
            webSocketManager.sendMessage(BusinessConstant.ONLINE_COUNT_INCREASE_MSG);
            return true;
        }else{
            return false;
        }
    }

    // 关闭 WebSocket 连接
    public void disconnectWebSocket() {
        webSocketManager.disConnect();
    }
}
