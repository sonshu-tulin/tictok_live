package com.bytedance.tictok_live.utils.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
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

import com.bytedance.tictok_live.constant.BusinessConstant;

/**
 * 直播管理
 */
public class LivePlayerManager {

    private static final String TAG = "LivePlayerManager";

    private ExoPlayer exoPlayer;

    private final Context appContext;
    private final PlayerView playerView;

    public LivePlayerManager(Context context, PlayerView playerView) {
        this.appContext = context.getApplicationContext();
        this.playerView = playerView;
    }

    /**
     * 初始化直播播放器
     */
    @OptIn(markerClass = UnstableApi.class)
    public void initPlayer() {

        // 如果已存在，先释放
        if (exoPlayer != null) {
            exoPlayer.release();
        }

        // 1. 轨道选择
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(appContext);
        TrackSelectionParameters params = new TrackSelectionParameters.Builder(appContext)
                .setMaxVideoSizeSd()
                .setMaxVideoFrameRate(30)
                .build();
        trackSelector.setParameters(params);

        // 2. 缓冲策略
        LoadControl liveLoadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        3000,   // minBuffer — 保障流畅
                        7000,   // maxBuffer
                        200,    // 首帧快速
                        1000    // 卡顿后恢复缓冲
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        // 3. 构建播放器
        ExoPlayer.Builder builder = new ExoPlayer.Builder(appContext)
                .setTrackSelector(trackSelector)
                .setLoadControl(liveLoadControl);

        exoPlayer = builder.build();
        playerView.setPlayer(exoPlayer);

        // 4. HTTP 数据源
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Media3-LivePlayer")
                .setConnectTimeoutMs(5000)
                .setReadTimeoutMs(5000)
                .setAllowCrossProtocolRedirects(true);


        // 5. 构建 DASH 直播流
        DashMediaSource dashMediaSource = new DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(BusinessConstant.LIVE_DASH_URL));

        exoPlayer.setMediaSource(dashMediaSource);
        exoPlayer.prepare();

        // 6. 监听
        addPlayerListener();
    }

    /**
     * 添加监听（首帧、其它异常）
     */
    private void addPlayerListener() {
        exoPlayer.addListener(new Player.Listener() {

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    Log.d(TAG, "首帧已就绪");
                    if (!exoPlayer.isPlaying()) {
                        exoPlayer.play();
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                handlePlayerError(error);
            }
        });
    }

    /**
     * 直播异常分类处理
     */
    private void handlePlayerError(PlaybackException error) {
        int code = error.errorCode;

        // 直播窗口越界 —— 最常见
        if (code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            Log.e(TAG, "BEHIND_LIVE_WINDOW，重置播放器追赶直播");
            resetPlayer();
            return;
        }

        // 网络问题
        if (code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
            Log.e(TAG, "网络异常，稍后重试：" + error);
            retryWithDelay();
            return;
        }

        // 解码器问题
        if (code == PlaybackException.ERROR_CODE_DECODING_FAILED) {
            Log.e(TAG, "解码器崩溃，重置播放器");
            resetPlayer();
            return;
        }

        // 未知错误 —— 最后兜底
        Log.e(TAG, "未知异常：" + error);
        resetPlayer();
    }

    /**
     * 重置直播播放器
     */
    public void resetPlayer() {
        if (exoPlayer == null) return;

        Log.d(TAG, "执行 resetPlayer()");

        try {
            exoPlayer.stop();
            exoPlayer.seekToDefaultPosition();  // 跳转到最新直播点
            exoPlayer.prepare();
            exoPlayer.play();
        } catch (Exception e) {
            Log.e(TAG, "resetPlayer() 出错", e);
        }
    }

    /**
     * 延迟 1 秒重试
     */
    private void retryWithDelay() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this::resetPlayer, 1000);
    }

    /**
     * 暂停
     */
    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    /**
     * 恢复
     */
    public void resume() {
        if (exoPlayer != null && !exoPlayer.isPlaying()) {
            exoPlayer.play();
        }
    }

    /**
     * 完整释放资源
     */
    public void release() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }


    /**
     * 是否为空
     */
    public boolean isPlayerNull() {
        return exoPlayer == null;
    }

    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        return exoPlayer.isPlaying();
    }
}
