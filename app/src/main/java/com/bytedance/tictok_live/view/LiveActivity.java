package com.bytedance.tictok_live.view;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
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
import com.bytedance.tictok_live.content.BusinessConstant;
import com.bytedance.tictok_live.recycler.CommentAdapter;
import com.bytedance.tictok_live.viewModel.LiveViewModel;

import java.util.ArrayList;

/**
 * View层，直播Activity
 */
public class LiveActivity extends AppCompatActivity {

    public static final String TAG = "LiveActivity";
    private LiveViewModel liveViewModel;

    // 控件
    private PlayerView playerView;
    private ImageView ivHostAvatar;
    private TextView tvHostName;
    private TextView tvHostFollower;
    private LinearLayout llOnlineContainer;
    private TextView tvCloseOnline;
    private RecyclerView rvComments;
    private EditText etSendComment;
    private TextView tvOnline;

    // 对象
    private ExoPlayer exoPlayer;

    private CommentAdapter commentAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);

        // 1. 初始化
        initView();

        // 2. 初始化 Media3 ExoPlayer 播放直播流
        initMedia3PlayerForLive();

        // 2. 获取 ViewModel 实例（由 ViewModelProvider 管理，页面重建不重新创建）
        liveViewModel = new ViewModelProvider(this).get(LiveViewModel.class);

        // 3. 观察 ViewModel 的数据，自动更新 UI (无需手动调用)
        observeViewModelData();

        // 4. 触发业务逻辑（只发指令）
        liveViewModel.loadHostInfo();
        liveViewModel.loadInitComments();

        // 5. 监听关闭在线人数控件
        listenCloseOnline();

        // 7. 监听回车发送评论
        listenEnterSendComment();
    }

    /**
     * 初始化
     */
    private void initView() {
        playerView = findViewById(R.id.player_view);
        ivHostAvatar = findViewById(R.id.iv_host_avatar);
        tvHostName = findViewById(R.id.tv_host_name);
        tvHostFollower = findViewById(R.id.tv_host_follower);
        llOnlineContainer = findViewById(R.id.ll_online_container);
        tvCloseOnline = findViewById(R.id.tv_close_online);
        etSendComment = findViewById(R.id.et_send_comment);
        tvOnline = findViewById(R.id.tv_online);

        // RecyclerView 相关
        rvComments = findViewById(R.id.rv_comments);
        commentAdapter = new CommentAdapter(new ArrayList<>());
        rvComments.setAdapter(commentAdapter);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
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
                .setAllowCrossProtocolRedirects(true); // 允许跨协议重定向

        // 4. 构建 DASH 直播源
        DashMediaSource dashMediaSource = new DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(BusinessConstant.LIVE_DASH_URL));

        // 5. 准备并启动播放
        exoPlayer.setMediaSource(dashMediaSource);
        exoPlayer.prepare(); // 加载直播流
        exoPlayer.play(); // 启动播放
    }

    /**
     * 观察 ViewModel 的数据，自动更新 UI
     */
    private void observeViewModelData() {
        Log.d(TAG,"进入观察");

        liveViewModel.getHostInfo().observe(this, hostInfo -> {
            Log.d(TAG,"观察到主播信息变化");
            // 填充头像
            Glide.with(this)
                    .load(hostInfo.getAvatar())
                    .circleCrop()
                    .error(R.mipmap.ic_launcher)
                    .placeholder(R.mipmap.ic_launcher)
                    .into(ivHostAvatar);

            // 填充主播名字、关注量
            tvHostName.setText(hostInfo.getName());
            tvHostFollower.setText("关注 " + hostInfo.getFollowerNum());

        });

        // 观察评论列表变化
        liveViewModel.getCommentList().observe(this, commentList -> {
            Log.d(TAG,"观察到评论变化");
            commentAdapter.setData(commentList);
            rvComments.scrollToPosition(commentAdapter.getItemCount() - 1);
        });

        // 观察在线人数变化
        liveViewModel.getOnlineCount().observe(this, onlineCount ->{
            Log.d(TAG,"观察到在线人数变化");
            tvOnline.setText(String.valueOf(onlineCount));
        });
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
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    // 去掉首尾空格
                    String commentContent = v.getText().toString().trim();

                    // 1. 发送评论
                    liveViewModel.sendComment(commentContent);

                    // 2. 清空输入框
                    etSendComment.setText("");

                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放播放器
        if (exoPlayer != null){
            exoPlayer.release();
            exoPlayer = null;
        }

        liveViewModel.disconnectWebSocket();
    }
}
