package com.bytedance.tictok_live.activity;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.tictok_live.R;
import com.bytedance.tictok_live.adapter.CommentAdapter;
import com.bytedance.tictok_live.model.Comment;
import com.bytedance.tictok_live.model.HostInfo;
import com.bytedance.tictok_live.network.HostApiService;
import com.bytedance.tictok_live.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LiveRoomActivity extends AppCompatActivity {
    private static final String TAG = "LiveRoomActivity";

    // 控件
    private PlayerView playerView;
    private ImageView ivHostAvatar;
    private TextView tvHostName;
    private TextView tvHostFollower;
    private LinearLayout llOnlineContainer;
    private TextView tvCloseOnline;
    private RecyclerView rvComments;
    private EditText etSendComment;

    // 对象
    private ExoPlayer exoPlayer;
    private HostInfo hostInfo;

    // Adapter
    private CommentAdapter commentAdapter;

    // 网络
    private HostApiService hostApiService;

    // DASH 直播源
    private static final String LIVE_DASH_URL = "https://akamaibroadcasteruseast.akamaized.net/cmaf/live/657078/akasource/out.mpd";

    // 评论最大长度
    public static final int COMMENT_MAX_LENGTH = 128;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);

        // 1. 初始化控件
        initViews();

        // 2. 初始化 Media3 ExoPlayer 播放直播流
        initMedia3PlayerForLive();

        // 3. 初始化API接口
        hostApiService = RetrofitClient.createApi(HostApiService.class);

        // 4. 加载数据
        loadData();

        // 5. 监听关闭在线人数控件
        listenCloseOnline();

        // 6. 在4. 的加载数据中获取公屏评论

        // 7. 监听回车发送评论
        listenEnterSendComment();
    }

    // 绑定控件
    private void initViews() {
        playerView = findViewById(R.id.player_view);
        ivHostAvatar = findViewById(R.id.iv_host_avatar);
        tvHostName = findViewById(R.id.tv_host_name);
        tvHostFollower = findViewById(R.id.tv_host_follower);
        llOnlineContainer = findViewById(R.id.ll_online_container);
        tvCloseOnline = findViewById(R.id.tv_close_online);
        rvComments = findViewById(R.id.rv_comments);
        etSendComment = findViewById(R.id.et_send_comment);
    }

    /**
     * 初始化 Media3 ExoPlayer
     */
    @OptIn(markerClass = UnstableApi.class)
    private void initMedia3PlayerForLive() {
        // 1. 用 Application 上下文（避免 Activity 内存泄漏）
        Context appContext = getApplicationContext();

        // 2. 配置直播优化参数：轨道选择、缓冲策略
        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(appContext)
                // 直播场景优先选择匹配设备能力的轨道（避免卡顿）
                .setTrackSelector(new DefaultTrackSelector(appContext))
                // 缓冲配置
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1500, 3000, 1000, 500)
                        .setPrioritizeTimeOverSizeThresholds(true) // 直播优先保证时间戳连续
                        .build());

        exoPlayer = playerBuilder.build();
        playerView.setPlayer(exoPlayer);

        // 3. 直播流配置：禁用缓存（直播不需要本地缓存，避免占用控件）
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Media3-LivePlayer/1.0")
                .setConnectTimeoutMs(5000) // 连接超时 5 秒
                .setReadTimeoutMs(5000) // 读取超时 5 秒
                .setAllowCrossProtocolRedirects(true); //允许跨协议重定向

        // 4. 构建 DASH 直播源
        DashMediaSource dashMediaSource = new DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(LIVE_DASH_URL));

        // 5. 准备并启动播放
        exoPlayer.setMediaSource(dashMediaSource);
        exoPlayer.prepare(); // 加载直播流
        exoPlayer.play(); // 启动播放
    }


    /**
     * 加载数据
     */
    private void loadData() {


        if (hostApiService == null) {
            Log.e(TAG, "HostApiService 初始化失败");
            showDefaultHostInfo();
            return;
        }

        // 2. 调用 API 获取主播信息（异步）
        hostApiService.getHostInfo().enqueue(new retrofit2.Callback<HostInfo>(){
            @Override
            public void onResponse(Call<HostInfo> call, Response<HostInfo> response) {
                if (response.isSuccessful() && response.body() != null){
                    hostInfo = response.body();
                    Log.d(TAG, "主播信息加载成功：" + hostInfo.toString());
                    //填充主播信息到UI
                    updateHostInfoUI();
                }else {
                    // 请求成功但无数据，显示默认信息
                    Log.w(TAG, "主播信息请求成功但无数据，code：" + response.code());
                    showDefaultHostInfo();
                }
            }

            @Override
            public void onFailure(Call<HostInfo> call, Throwable t) {
                // 失败显示默认信息
                Log.e(TAG, "主播信息请求失败", t);
                showDefaultHostInfo();
                t.printStackTrace();
            }
        });

        // 3. 调用 API 获取公屏评论（异步）
        hostApiService.getComments().enqueue(new retrofit2.Callback<List<Comment>>(){

            @Override
            public void onResponse(Call<List<Comment>> call, Response<List<Comment>> response) {
                if (response.isSuccessful() && response.body() !=null){
                    // 设置布局
                    if (rvComments.getLayoutManager() == null) {
                        LinearLayoutManager layoutManager = new LinearLayoutManager(LiveRoomActivity.this);
                        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
                        rvComments.setLayoutManager(layoutManager);
                    }

                    // 过虑空或过长评论
                    ArrayList<Comment> validComments = new ArrayList<>();
                    for (Comment comment : response.body()) {
                        if (!TextUtils.isEmpty(comment.getComment()) && comment.getComment().length() <= COMMENT_MAX_LENGTH){
                            validComments.add(comment);
                        }
                    }

                    // 给适配器添加评论
                    commentAdapter = new CommentAdapter(validComments);
                    rvComments.setAdapter(commentAdapter);
                    rvComments.scrollToPosition(commentAdapter.getItemCount() - 1);
                }else{
                    Log.w(TAG, "评论信息请求成功但无数据，code：" + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Comment>> call, Throwable t) {
                Log.e(TAG, "评论信息请求失败", t);
                t.printStackTrace();
            }
        });
    }

    // 更新主播信息到UI
    private void updateHostInfoUI() {
        if (hostInfo == null) return;

        // 加载网络头像（使用Glide）
        Glide.with(this)
                .load(hostInfo.getAvatar())
                .circleCrop()
                .error(R.mipmap.ic_launcher)
                .placeholder(R.mipmap.ic_launcher)
                .into(ivHostAvatar);

        // 填充主播名字、关注量
        tvHostName.setText(hostInfo.getName());
        tvHostFollower.setText("关注 " + hostInfo.getFollowerNum());

    }

    // 显示默认主播信息
    private void showDefaultHostInfo() {
        ivHostAvatar.setImageResource(R.mipmap.ic_launcher);
        tvHostName.setText("默认主播");
        tvHostFollower.setText("0");
    }

    /**
     * 监听在线人数关闭控件
     */
    private void listenCloseOnline() {
        tvCloseOnline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                llOnlineContainer.setVisibility(View.GONE);
            }
        });

    }

    /**
     * 监听回车发送评论
     */
    private void listenEnterSendComment() {
        etSendComment.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND){
                    // 去掉首尾空格
                    String commentContent = v.getText().toString().trim();
                    // 1. 内容为空或过长
                    if (TextUtils.isEmpty(commentContent) && commentContent.length() > COMMENT_MAX_LENGTH){
                        return true;
                    }

                    // 2. 发送评论
                    sendComment(commentContent);

                    // 3. 清空输入框
                    etSendComment.setText("");

                    return true;
                }
                return false;
            }
        });
    }

    /**
     * 发送评论
     * @param commentContent 评论内容
     */
    private void sendComment(String commentContent) {
        hostApiService.sendComment(commentContent).enqueue(new Callback<Comment>() {
            @Override
            public void onResponse(Call<Comment> call, Response<Comment> response) {
                if (response.isSuccessful() && response.body() != null){
                    Comment newComment = response.body();
                    commentAdapter.addComment(newComment);
                    rvComments.smoothScrollToPosition(commentAdapter.getItemCount() - 1);  //滑动到最后
                    Log.d(TAG,"发送成功:" + newComment);
                }else {
                    Log.d(TAG, "发送失败：API返回错误");
                }
            }

            @Override
            public void onFailure(Call<Comment> call, Throwable t) {
                Toast.makeText(LiveRoomActivity.this,"评论失败，请检查网络", Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (exoPlayer != null) {
            exoPlayer.play(); // 恢复播放
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.pause(); // 暂停播放
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}