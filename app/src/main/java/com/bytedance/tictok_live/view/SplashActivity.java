package com.bytedance.tictok_live.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bytedance.tictok_live.R;
import com.bytedance.tictok_live.utils.preload.LivePreloadManager;

/**
 * 直播启动页：负责预加载核心资源，提升 LiveActivity 启动体验
 */
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    private static final long SPLASH_MS = 2000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean hasNavigated = false;

    private LivePreloadManager livePreloadManager;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        Log.d(TAG,"开始预加载");

        livePreloadManager = LivePreloadManager.getInstance();
        livePreloadManager.startPreload();

        // 2s 后跳转
        mainHandler.postDelayed(this::navigateToLive, SPLASH_MS);
    }

    private void navigateToLive(){
        if (hasNavigated || isFinishing()) return;
        hasNavigated = true;

        Intent it = new Intent(this, LiveActivity.class);
        startActivity(it);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }
}