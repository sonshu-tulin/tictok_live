package com.bytedance.tictok_live.utils.monitor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流畅性相关监控
 *
 * 使用 Choreographer.FrameCallback 来监控 FPS（帧率）和丢帧。
 * 原理：
 *  1. Choreographer 每渲染一帧会回调 doFrame(long frameTimeNanos)
 *  2. 通过计算两次 frameTimeNanos 的间隔得到帧率（FPS）
 *  3. 如果两帧间隔大于阈值，视为丢帧
 */
public class FluencyMonitor {
    public static final String TAG = "LivePlayerMonitor";

    // 用于在主线程操作 Choreography
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 帧回调对象
    private Choreographer.FrameCallback frameCallback;

    // 上一帧的时间（纳秒）
    private long lastFrameTimeNanos = 0;

    // 当前帧率（近似）
    private final AtomicInteger fps = new AtomicInteger(0);

    // 累计丢帧次数
    private final AtomicInteger droppedFrames = new AtomicInteger(0);

    // 丢帧阈值（默认16.6ms，即60帧/秒的单帧耗时）
    public static final long DROPPED_FRAME_THRESHOLD_NS = 16_666_667L;

    // 运行标记
    private boolean running = false;

    // 日志
    // 日志输出间隔：1秒（转纳秒）
    private static final long LOG_INTERVAL_NS = 1_000_000_000L;
    // 上一次输出日志的时间（纳秒）
    private long lastLogTimeNanos = 0;
    // 1秒内累计的帧间隔总和（用于计算平均FPS）
    private long frameIntervalSum = 0;
    // 1秒内的总帧数
    private int frameCount = 0;

    /**
     * 开始监控
     */
    public void start() {
        if (running) return; // 已经在监控
        running = true;

        // 定义帧回调逻辑
        frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                // 非首帧才计算帧率和丢帧
                if (lastFrameTimeNanos != 0) {
                    // 帧间隔
                    long diff = frameTimeNanos - lastFrameTimeNanos;
                    // 累计帧间隔和帧数
                    frameIntervalSum += diff;
                    frameCount++;

                    // 计算实时FPS
                    int currentFps = (int) Math.round(1_000_000_000.0 / diff);
                    fps.set(currentFps);

                    // 判断是否丢帧并累计
                    if (diff > DROPPED_FRAME_THRESHOLD_NS) {
                        droppedFrames.incrementAndGet();
                    }

                    // 检查是否达到1秒输出间隔
                    long timeSinceLastLog = frameTimeNanos - lastLogTimeNanos;
                    if (timeSinceLastLog >= LOG_INTERVAL_NS) {
                        // 计算1秒内的平均FPS（更准确）
                        int averageFps = frameCount == 0
                                ? 0
                                : (int) Math.round((double) frameCount * 1_000_000_000L / frameIntervalSum);

                        // 输出日志
                        Log.d(TAG,
                                "1秒统计 -> 平均FPS = " + averageFps
                                        // + ", 累计丢帧总数 = " + droppedFrames.get()
                                        + ", 1秒内帧数 = " + frameCount);

                        // 重置统计变量，准备下一个5秒
                        lastLogTimeNanos = frameTimeNanos;
                        frameIntervalSum = 0;
                        frameCount = 0;
                    }
                } else {
                    // 首帧初始化日志时间
                    lastLogTimeNanos = frameTimeNanos;
                }

                // 更新上一帧时间
                lastFrameTimeNanos = frameTimeNanos;

                // 注册下一帧回调，实现循环监控
                if (running) { // 防止停止后仍回调
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        };

        // 延迟启动，确保 UI 已经 attach
        mainHandler.post(() -> Choreographer.getInstance().postFrameCallback(frameCallback));
    }

    /**
     * 停止监控
     */
    public void stop(){
        if (!running) return;
        running = false;
        if (frameCallback != null){
            try {
                // 移除帧回调
                Choreographer.getInstance().removeFrameCallback(frameCallback);
            }catch (Exception e){
                Log.e(TAG,"移除帧回调失败：" + e.getMessage());
            }
            frameCallback = null;
        }
        lastFrameTimeNanos = 0;
        lastLogTimeNanos = 0;
        frameIntervalSum = 0;
        frameCount = 0;
        fps.set(0);
        droppedFrames.set(0);
    }

    /**
     * 获取当前帧率
     */
    public int getFps(){
        return fps.get();
    }

    /**
     * 重置丢帧计数器
     */
    public void resetDroppedFrames(){
        droppedFrames.set(0);
    }

}