package com.bytedance.tictok_live.viewModel;

import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bytedance.tictok_live.constant.BusinessConstant;
import com.bytedance.tictok_live.model.Comment;
import com.bytedance.tictok_live.model.HostInfo;
import com.bytedance.tictok_live.repository.LiveRepository;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 直播ViewModel
 */
public class LiveViewModel extends ViewModel {

    private static final String TAG = "LiveViewModel";

    // 持有 Model 层实例
    private LiveRepository liveRepository;

    // 暴露给 View 的可观察状态（在线人数、评论列表）
    private MutableLiveData<HostInfo> hostInfo;
    private MutableLiveData<Integer> onlineCount;
    private MutableLiveData<List<Comment>> commentList;

    public LiveViewModel() {
        liveRepository = new LiveRepository();

        hostInfo = new MutableLiveData<>();
        onlineCount = new MutableLiveData<>(BusinessConstant.ONLINE_COUNT_INIT_VALUE);
        commentList = new MutableLiveData<>();

        initWebSocketListener();
    }

    // 给 View 暴露可观察数据
    public LiveData<HostInfo> getHostInfo() {
        return hostInfo;
    }

    public LiveData<Integer> getOnlineCount() {
        return onlineCount;
    }

    public LiveData<List<Comment>> getCommentList() {
        return commentList;
    }

    // WebSocket 监听在线人数
    private void initWebSocketListener() {
        liveRepository.observeWebSocketMessage(message -> {
            if (BusinessConstant.ONLINE_COUNT_INCREASE_MSG.equals(message)) {
                int current = onlineCount.getValue() == null ? 0 : onlineCount.getValue();
                onlineCount.postValue(current + 1);
            }
        });
    }

    // 获取主播信息
    public void loadHostInfo() {
        liveRepository.getHostInfo(new Callback<HostInfo>() {
            @Override
            public void onResponse(Call<HostInfo> call, Response<HostInfo> response) {
                if (response.isSuccessful() && response.body() != null) {
                    HostInfo info = response.body();
                    hostInfo.postValue(info);
                    Log.d(TAG, "主播信息加载成功：" + info.toString());
                } else {
                    Log.w(TAG, "主播信息请求成功但无数据，code：" + response.code());
                }
            }

            @Override
            public void onFailure(Call<HostInfo> call, Throwable t) {
                Log.e(TAG, "主播信息请求失败", t);
                t.printStackTrace();
            }
        });
    }

    // 业务逻辑：获取初始评论（公屏）
    public void loadInitComments() {
        liveRepository.getInitComments(new Callback<List<Comment>>() {
            @Override
            public void onResponse(Call<List<Comment>> call, Response<List<Comment>> response) {
                // 过虑空或过长评论
                ArrayList<Comment> validComments = new ArrayList<>();
                for (Comment comment : response.body()) {
                    if (!TextUtils.isEmpty(comment.getComment()) && comment.getComment().length() <= BusinessConstant.COMMENT_MAX_LENGTH) {
                        validComments.add(comment);
                    }
                }
                // 更新评论列表，View 自动刷新
                commentList.postValue(validComments);
            }

            @Override
            public void onFailure(Call<List<Comment>> call, Throwable t) {
                Log.e(TAG, "获取评论失败");
            }
        });
    }

    // 业务逻辑：发送评论
    public void sendComment(String commentContent) {

        // 内容为空或过长
        if (TextUtils.isEmpty(commentContent) || commentContent.length() > BusinessConstant.COMMENT_MAX_LENGTH) {
            return;
        }

        liveRepository.sendComment(commentContent, new Callback<Comment>() {
            @Override
            public void onResponse(Call<Comment> call, Response<Comment> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Comment newComment = response.body();
                    List<Comment> currentList = commentList.getValue();
                    if (currentList == null) {
                        currentList = new ArrayList<>();
                    }
                    currentList.add(newComment);
                    commentList.postValue(currentList);
                    Log.d(TAG, "评论发送成功： " + newComment.getComment());

                    // 触发在线人数增加
                    triggerOnlineCountIncrease();
                } else {
                    Log.w(TAG, "评论发送失败，响应码：" + response.code());
                }
            }

            @Override
            public void onFailure(Call<Comment> call, Throwable t) {
                Log.e(TAG, "评论发送失败,请检查网络", t);
                t.printStackTrace();
            }
        });
    }

    // 触发在线人数增加
    private void triggerOnlineCountIncrease() {
        boolean isWsSendSuccess = liveRepository.sendOnlineCountIncreaseMsg();
        if (isWsSendSuccess) {
            Log.d(TAG, "发送评论成功，触发 WS 在线人数+1");
        } else {
            int currentCount = onlineCount.getValue() == null ? 0 : onlineCount.getValue();
            onlineCount.postValue(currentCount + 1);
            Log.e(TAG, "WS未连接，降级处理，本地更新在线人数：" + (currentCount + 1));
        }

    }

    // 断开连接
    public void disconnectWebSocket() {
        liveRepository.disconnectWebSocket();
    }

    // 暂停WebSocket
    public void pauseWebSocket() {
        Log.d(TAG, "暂停消息接收");
        liveRepository.pauseWebSocket();
    }

    // 恢复消息接收
    public void resumeWebSocket() {
        Log.d(TAG, "恢复消息接收");
        liveRepository.resumeWebSocket();
    }

    //释放 WebSocket 连接
    public void releaseWebSocket(){
        Log.d(TAG, "释放WebSocket连接");
        liveRepository.releaseWebSocket();
    }

}
