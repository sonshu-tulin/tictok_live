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
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.tictok_live.R;
import com.bytedance.tictok_live.constant.BusinessConstant;
import com.bytedance.tictok_live.model.Comment;
import com.bytedance.tictok_live.recycler.CommentAdapter;
import com.bytedance.tictok_live.viewModel.LiveViewModel;

import java.util.ArrayList;

/**
 * View层，直播Activity
 */
public class LiveActivity extends AppCompatActivity {

    public static final String TAG = "LiveActivity";

    // 持有 viewModel 层实例
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

    // 标记是否是首帧
    private boolean isFirstPlay = true;

    // 标记是否是临时切后台，避免重复释放
    private boolean isTempBackground = false;

    // // 记录上一次的评论数量，标记是否是首次全量加载评论
    private int lastCommentCount = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);

        // 1. 初始化
        initView();

        // 2. 初始化 Media3 ExoPlayer 播放直播流
        initMedia3PlayerForLive();

        // 3. 获取 ViewModel 实例（由 ViewModelProvider 管理，页面重建不重新创建）
        liveViewModel = new ViewModelProvider(this).get(LiveViewModel.class);

        // 4. 观察 ViewModel 的数据，自动更新 UI (无需手动调用)
        observeViewModelData();

        // 5. 触发业务逻辑（只发指令）
        liveViewModel.loadHostInfo();
        liveViewModel.loadInitComments();

        // 6. 监听关闭在线人数控件
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

        // 2. 轨道选择优化（优先低分辨率，加快解码）
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(appContext);
        TrackSelectionParameters trackParams = new TrackSelectionParameters.Builder(appContext)
                .setMaxVideoSizeSd() // 优先标清轨道
                .setMaxVideoFrameRate(30) // 限制30fps，减少解码耗时
                .build();
        trackSelector.setParameters(trackParams);

        // 3. 配置缓冲策略(首帧)
        LoadControl firstFrameLoadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        500,  // 最小缓冲（ms）
                        4000,  // 最大缓冲（ms）
                        200,   // 缓冲播放阈值（ms）
                        500    // 缓冲重试阈值（ms）
                )
                .setPrioritizeTimeOverSizeThresholds(true) // 直播优先保证时间戳连续
                .build();

        // 4. HTTP 数据源优化
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Media3-LivePlayer/1.0")
                .setConnectTimeoutMs(5000) // 连接超时 5 秒
                .setReadTimeoutMs(5000) // 读取超时 5 秒
                .setAllowCrossProtocolRedirects(true); // 允许跨协议重定向

        // 5. 构建播放器()
        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(appContext)
                .setTrackSelector(trackSelector)
                .setLoadControl(firstFrameLoadControl);

        exoPlayer = playerBuilder.build();
        playerView.setPlayer(exoPlayer);

        // 6. 构建 DASH 直播源
        DashMediaSource dashMediaSource = new DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(BusinessConstant.LIVE_DASH_URL));

        // 异步准备 + 首帧监听（避免主线程阻塞）
        exoPlayer.setPlayWhenReady(false);
        exoPlayer.setMediaSource(dashMediaSource);
        exoPlayer.prepare(); // 加载直播流

        // 播放器监听
        exoPlayer.addListener(new ExoPlayer.Listener() {

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Player.Listener.super.onPlaybackStateChanged(playbackState);
                // 首帧就绪且首次播放时立即播放
                if (playbackState == ExoPlayer.STATE_READY && !exoPlayer.isPlaying() && isFirstPlay) {
                    exoPlayer.play();
                    isFirstPlay = false;
                }
            }

            // 异常处理（直播窗口越界）
            @Override
            public void onPlayerError(PlaybackException error) {
                Player.Listener.super.onPlayerError(error);
                if ("ERROR_CODE_BEHIND_LIVE_WINDOW".equals(error.getErrorCodeName())) {
                    resetPlayer(); // 触发播放器重置
                }
            }
        });

    }

    /**
     * 重置播放器（处理直播窗口越界）
     */
    @OptIn(markerClass = UnstableApi.class)
    private void resetPlayer() {
        if (exoPlayer == null) return;
        exoPlayer.stop();
        exoPlayer.clearMediaItems();

        // 重建数据源
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Media3-LivePlayer/1.0")
                .setConnectTimeoutMs(5000)
                .setReadTimeoutMs(5000)
                .setAllowCrossProtocolRedirects(true);

        // 重建播放器
        ExoPlayer newPlayer = new ExoPlayer.Builder(getApplicationContext())
                .setTrackSelector(new DefaultTrackSelector(getApplicationContext()))
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(500, 6000, 200, 500)
                        .build())
                .build();

        // 替换播放器
        playerView.setPlayer(newPlayer);
        exoPlayer.release();
        exoPlayer = newPlayer;

        // 重新加载直播流
        DashMediaSource dashMediaSource = new DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(BusinessConstant.LIVE_DASH_URL));
        exoPlayer.setMediaSource(dashMediaSource);
        exoPlayer.prepare();
        exoPlayer.play();
        isFirstPlay = true;
    }

    /**
     * 观察 ViewModel 的数据，自动更新 UI
     */
    private void observeViewModelData() {
        Log.d(TAG, "进入观察");

        liveViewModel.getHostInfo().observe(this, hostInfo -> {
            Log.d(TAG, "观察到主播信息变化");
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
        liveViewModel.getCommentList().observe(this, newComments -> {
            Log.d(TAG, "观察到评论变化，当前列表长度：" + (newComments != null ? newComments.size() : 0));
            if (newComments == null) return;

            int newCount = newComments.size();

            // 场景1：首次加载（上次长度为 0，全量刷新）
            if (lastCommentCount == 0){
                Log.d(TAG, "首次加载");
                commentAdapter.setData(newComments);
            }
            // 场景2：新增单条评论（局部刷新）
            else if (newCount == lastCommentCount + 1) {
                Log.d(TAG, "局部刷新");
                Comment newComment = newComments.get(newCount - 1);
                commentAdapter.addComment(newComment);
            }
            // 场景3：其它情况（列表重置....）
            else {
                Log.d(TAG,"其它情况");
                commentAdapter.setData(newComments);
            }

            // 更新上次长度
            lastCommentCount = newCount;
            rvComments.scrollToPosition(commentAdapter.getItemCount() - 1);
        });

        // 观察在线人数变化
        liveViewModel.getOnlineCount().observe(this, onlineCount -> {
            Log.d(TAG, "观察到在线人数变化");
            String onlineText = onlineCount == null ? "0" : String.valueOf(onlineCount);
            tvOnline.setText(onlineText);
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

    /**
     * 前台恢复
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "进入onResume");
        // 恢复 Websocket
        liveViewModel.resumeWebSocket();
        // 设置常亮
        playerView.setKeepScreenOn(true);

        // 临时切换后台返回，仅恢复播放
        if (isTempBackground) {
            if (exoPlayer != null) {
                exoPlayer.play();
            }
            isTempBackground = false; // 重置标记
        }
        // 非临时后台切换,播放器已经没了,重新初始化播放器
        else if (exoPlayer == null) {
            initMedia3PlayerForLive();
        }
        // 有弹窗,关闭后,正常前台恢复，确保播放
        else {
            if (!exoPlayer.isPlaying()) {
                exoPlayer.play();
            }
        }
    }

    /**
     * 临时暂停，切换后台
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "进入onPause");
        if (exoPlayer != null) {
            exoPlayer.pause(); // 暂停播放
            playerView.setKeepScreenOn(false);
        }
        liveViewModel.pauseWebSocket(); // 暂停接收消息
    }

    /**
     * 后台不可见：仅仅临时切换后台不是放资源，彻底销毁才释放
     */
    @Override
    protected void onStop() {
        Log.d(TAG, "进入onStop");
        super.onStop();
        // 区分「临时后台」和「彻底销毁/配置变更」
        if (!isFinishing() && !isChangingConfigurations()) {
            // 标记为临时切换后台
            isTempBackground = true;
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(false); // 确保暂停
            }
        } else {
            // 彻底销毁/配置变更：释放播放器 + 断开 WebSocket 连接
            if (exoPlayer != null) {
                exoPlayer.release();
                exoPlayer = null;
            }
            liveViewModel.disconnectWebSocket();
        }

    }

    /**
     * 销毁
     */
    @Override
    protected void onDestroy() {
        Log.d(TAG, "进入onDestroy");
        super.onDestroy();
        // 释放播放器
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        // 释放连接
        liveViewModel.releaseWebSocket();
    }
}
