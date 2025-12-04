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
import com.bytedance.tictok_live.utils.preload.LivePreloadManager;

/**
 * 直播管理
 */
public class LivePlayerManager {

    private static final String TAG = "LivePlayerManager";

    private static volatile LivePlayerManager instance;

    private final Context appContext;

    // 播放器
    private ExoPlayer exoPlayer;
    private PlayerView playerView;

    // 状态标记
    private boolean coreInitialized = false;     // 是否初始化
    private boolean mediaPrepared = false;       // 是否加载 media source
    private boolean firstFrameSeen = false;      // 首帧是否完成
    private long initStartTime = -1;             // 首帧统计


    public LivePlayerManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static LivePlayerManager getInstance(Context ctx) {
        if (instance == null) {
            synchronized (LivePlayerManager.class) {
                if (instance == null) {
                    instance = new LivePlayerManager(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 初始化核心播放器（用于启动页）
     */
    @OptIn(markerClass = UnstableApi.class)
    public synchronized void initPlayer() {
        // 如果已经初始化
        if (coreInitialized) return;

        coreInitialized = true;

        initStartTime = System.currentTimeMillis();

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

        // 4. 监听首帧、异常
        addPlayerListener();

        if (playerView != null) {
            playerView.setPlayer(exoPlayer);
        }

        Log.d(TAG, "核心播放器初始化完成（未prepare），耗时：" + (System.currentTimeMillis() - initStartTime) + "ms");
    }

    /**
     * 继续加载媒体资源
     */
    @OptIn(markerClass = UnstableApi.class)
    public synchronized void prepareIfNeeded(){
        if (mediaPrepared) {
            Log.d(TAG, "DASH媒体资源已加载，跳过重复prepare");
            return;
        }
        if (exoPlayer == null) {
            Log.e(TAG, "播放器未初始化，无法加载媒体资源");
            return;
        }

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Live-Player")
                .setConnectTimeoutMs(5000)
                .setReadTimeoutMs(5000)
                .setAllowCrossProtocolRedirects(true);

        DashMediaSource dashMediaSource = new DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(BusinessConstant.LIVE_DASH_URL));

        exoPlayer.setMediaSource(dashMediaSource);
        exoPlayer.prepare();

        mediaPrepared = true;
        Log.d(TAG, "prepareIfNeeded(): 直播媒体加载开始");
    }

    /**
     * UI 绑定播放器
     */
    public synchronized  void attachPlayerView(PlayerView view){
        this.playerView = view;
        if (exoPlayer != null){
            view.setPlayer(exoPlayer);
        }
    }

    /**
     * UI 解除绑定
     */
    public synchronized void detachPlayerView() {
        if (playerView != null) {
            // 不释放播放器（由 preload 管理），只是解除绑定
            playerView.setPlayer(null);
            playerView = null;
        }
    }

    /**
     * 添加监听（首帧、其它异常）
     */
    private void addPlayerListener() {
        exoPlayer.addListener(new Player.Listener() {

            @Override
            public void onPlaybackStateChanged(int state) {
                if (!firstFrameSeen && state == Player.STATE_READY) {
                    firstFrameSeen = true;
                    int ttffMs = (int) (System.currentTimeMillis() - initStartTime);
                    Log.d(TAG, "首帧渲染时间 TTFF = " + ttffMs + " ms");

                    // 播放器自动播放
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
        detachPlayerView();
        coreInitialized = false;
        mediaPrepared = false;
        firstFrameSeen = false;
        initStartTime = -1;
        Log.d(TAG, "播放器资源释放完成");
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

    /**
     * 复用预加载初始化，失败则兜底重新创建
     */
    public void initPlayerWithPreload(PlayerView playerView) {
        this.playerView = playerView;

        // 1. 获取预加载管理器，尝试复用预加载实例
        LivePreloadManager preloadManager = LivePreloadManager.getInstance();
        LivePlayerManager preloadPlayer = preloadManager.getPreloadPlayer();

        if (preloadPlayer != null && !preloadPlayer.isPlayerNull()){
            // 场景1：复用预加载的播放器实例
            Log.d(TAG, "复用预加载的播放器实例");
            // 接管预加载的实例的核心资源
            this.exoPlayer = preloadPlayer.getExoPlayer();
            this.coreInitialized = preloadPlayer.coreInitialized;
            this.mediaPrepared = preloadPlayer.mediaPrepared;

            // 绑定控件 + 继续加载 + 恢复播放
            attachPlayerView(playerView);
            prepareIfNeeded();
            resume();
        }else {
            // 场景2：预加载失败，异步兜底初始化
            Log.w(TAG,"预加载播放器为空，兜底初始化");
            new Thread(() -> {
                // 子线程初始化核心播放器
                initPlayer();
                // 切回主线程绑定控件
                new Handler(Looper.getMainLooper()).post(() -> {
                    attachPlayerView(playerView);
                    prepareIfNeeded();
                    resume();
                });
            }).start();
        }
    }

    private ExoPlayer getExoPlayer() {
        return exoPlayer;
    }
}
