package com.bytedance.tictok_live.activity;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

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

import com.bumptech.glide.Glide;
import com.bytedance.tictok_live.R;
import com.bytedance.tictok_live.model.HostInfo;
import com.bytedance.tictok_live.network.HostApiService;
import com.bytedance.tictok_live.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Response;

public class LiveRoomActivity extends AppCompatActivity {
    private static final String TAG = "LiveRoomActivity";

    // 控件
    private PlayerView playerView;
    private ImageView ivHostAvatar;
    private TextView tvHostName;

    private TextView tvHostFollower;

    // 对象
    private ExoPlayer exoPlayer;
    private HostInfo hostInfo;

    // 网络
    private HostApiService hostApiService;

    // DASH 直播源
    private static final String LIVE_DASH_URL = "https://akamaibroadcasteruseast.akamaized.net/cmaf/live/657078/akasource/out.mpd";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);

        // 1. 初始化控件
        initViews();
        // 2. 初始化 Media3 ExoPlayer 播放直播流
        initMedia3PlayerForLive();
        // 3. 加载数据
        loadData();
    }

    // 绑定控件
    private void initViews() {
        playerView = findViewById(R.id.player_view);
        ivHostAvatar = findViewById(R.id.iv_host_avatar);
        tvHostName = findViewById(R.id.tv_host_name);
        tvHostFollower = findViewById(R.id.tv_host_follower);

        // 防御性判空：检查控件是否初始化成功
        if (playerView == null || ivHostAvatar == null || tvHostName == null || tvHostFollower == null) {
            Log.e(TAG, "控件初始化失败，请检查布局文件中的ID是否正确");
        }
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
        // 1. 初始化API接口
        hostApiService = RetrofitClient.createApi(HostApiService.class);

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