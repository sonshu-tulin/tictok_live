package com.bytedance.tictok_live.activity;


import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * 直播
 */
public class LiveRoomActivity extends AppCompatActivity {
    private ExoPlayer exoPlayer;

    private PlayerView playerView;

    // 直播源
    private static final String LIVE_DASH_URL = "https://livesim2.dashif.org/livesim2/chunkdur_1/ato_7/testpic4_8s/Manifest300.mpd";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }
}
