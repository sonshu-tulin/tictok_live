package com.bytedance.tictok_live.utils.preload;

import android.util.Log;

import com.bytedance.tictok_live.context.App;
import com.bytedance.tictok_live.model.HostInfo;
import com.bytedance.tictok_live.repository.LiveRepository;
import com.bytedance.tictok_live.utils.player.LivePlayerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 预加载管理:管理直播播放的基础资源
 */
public class LivePreloadManager {
    public static final String TAG = "LivePreloadManager";

    private static volatile LivePreloadManager instance;

    // 线程池
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private LiveRepository liveRepository;
    private LivePlayerManager preloadPlayer;


    // 内存缓存
    private volatile HostInfo cachedHostInfo = null;

    // 预加载状态
    private volatile boolean preloadStarted = false;
    private volatile boolean hostInfoLoaded = false;    // 主播信息加载完成标记
    private volatile boolean playerInited = false;      // 播放器初始化完成标记


    private LivePreloadManager() {
        liveRepository = new LiveRepository();
    }

    public static LivePreloadManager getInstance(){
        if (instance == null){
            synchronized (LivePreloadManager.class){
                if (instance == null){
                    instance = new LivePreloadManager();
                }
            }
        }
        return instance;
    }

    /**
     * 启动预加载
     */
    public void startPreload(){
        if (preloadStarted) return;

        // 初始化状态
        preloadStarted = true;
        hostInfoLoaded = false;
        playerInited = false;

        // 并发请求主播信息
        executor.submit(() ->{
            liveRepository.getHostInfo(new Callback<HostInfo>() {
                @Override
                public void onResponse(Call<HostInfo> call, Response<HostInfo> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        HostInfo info = response.body();
                        cachedHostInfo = info;
                        Log.d(TAG, "主播信息预加载成功：" + info.toString());
                    } else {
                        Log.w(TAG, "主播信息请求成功但无数据，code：" + response.code());
                    }
                    // 无论成功失败都标记完成
                    hostInfoLoaded = true;
                }

                @Override
                public void onFailure(Call<HostInfo> call, Throwable t) {
                    Log.e(TAG, "主播信息请求失败", t);
                    hostInfoLoaded = true;
                }
            });
        });

        // 并发请求加载直播流播放器
        executor.submit(() -> {
            try {
                preloadPlayer = new LivePlayerManager(App.getAppContext());
                preloadPlayer.initPlayer();
                Log.d(TAG, "直播播放器预加载完成");
                playerInited = true; // 初始化完成标记
            } catch (Exception e) {
                Log.e(TAG, "播放器预加载失败", e);
                playerInited = true; // 失败也标记，避免阻塞
            }
        });
    }

    /**
     * 资源释放
     */
    public void release(){
        preloadStarted = false;
        hostInfoLoaded = false;
        playerInited = false;

        // 清空缓存
        cachedHostInfo = null;

        // 释放播放器
        if (preloadPlayer != null) {
            preloadPlayer.release();
            preloadPlayer = null;
        }

        executor.shutdown();
        Log.d(TAG, "预加载资源已全部释放");

    }

    // 外部查询缓存
    public HostInfo getCachedHostInfo() {
        return cachedHostInfo;
    }

    public LivePlayerManager getPreloadPlayer() {
        return preloadPlayer;
    }

    public boolean isHostInfoLoaded() {
        return hostInfoLoaded;
    }

    public boolean isPlayerInited() {
        return playerInited;
    }

    public boolean isPreloadStarted() {
        return preloadStarted;
    }


}
