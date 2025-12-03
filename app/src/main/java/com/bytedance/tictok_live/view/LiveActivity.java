package com.bytedance.tictok_live.view;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.tictok_live.R;
import com.bytedance.tictok_live.model.Comment;
import com.bytedance.tictok_live.recycler.CommentAdapter;
import com.bytedance.tictok_live.utils.monitor.FluencyMonitor;
import com.bytedance.tictok_live.utils.player.LivePlayerManager;
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
    private CommentAdapter commentAdapter;

    private LivePlayerManager livePlayerManager;

    // 标记是否是临时切后台，避免重复释放
    private boolean isTempBackground = false;

    // // 记录上一次的评论数量，标记是否是首次全量加载评论
    private int lastCommentCount = 0;

    // 流畅性监控
    private FluencyMonitor fluencyMonitor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_room);

        // 1. 初始化
        initView();

        // 2. 播放直播流
        livePlayerManager = new LivePlayerManager(this, playerView);
        livePlayerManager.initPlayer();

        // 启动流畅性监控
        fluencyMonitor = new FluencyMonitor();
        fluencyMonitor.start();

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
        fluencyMonitor.start();

        // 情况 1：只是临时切入后台，回到前台后恢复播放
        if (isTempBackground) {
            livePlayerManager.resume();
            isTempBackground = false;
            return;
        }

        // 情况 2：如果之前播放器已经被销毁，需要重新初始化
        if (livePlayerManager.isPlayerNull()) {
            livePlayerManager.initPlayer();
            return;
        }

        // 情况 3：正常前台恢复，确保正在播放
        if (!livePlayerManager.isPlaying()) {
            livePlayerManager.resume();
        }
    }

    /**
     * 临时暂停，切换后台
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "进入onPause");
        fluencyMonitor.stop();

        livePlayerManager.pause();
        playerView.setKeepScreenOn(false);
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
        boolean activityStillExists = !isFinishing() && !isChangingConfigurations();

        if (activityStillExists) {
            // 情况 1：普通后台切换（Activity 未真正销毁）
            isTempBackground = true;
            livePlayerManager.pause();
        } else {
            // 情况 2：彻底销毁或配置变化 → 完全释放资源
            livePlayerManager.release();
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
        livePlayerManager.release();
        // 释放连接
        liveViewModel.releaseWebSocket();
    }
}
