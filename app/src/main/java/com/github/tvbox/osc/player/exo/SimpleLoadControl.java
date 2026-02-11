package com.github.tvbox.osc.player.exo;

import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import java.util.HashMap;

/**
 * 自定义LoadControl - 确保暂停时也继续缓冲
 *
 * 核心行为：
 * 1. 缓冲区不按照大小设置，仅设置最大限制
 * 2. 缓冲区仅看缓存了多长的时间影片，除直播外，永远缓存超前1min
 * 3. 暂停可以继续缓存，直到缓存满1min或者达到缓冲区内存最大限制
 * 4. 一旦开始播放，立即继续缓存
 */
public class SimpleLoadControl extends DefaultLoadControl {

    private final long maxBufferMs;

    public SimpleLoadControl(long maxBufferMs) {
        super(
            new DefaultAllocator(true, 640 * 1024),  // allocator: 640MB内存限制
            1000,                    // minBufferMs: 最小1秒
            1000,                    // minBufferForLocalPlaybackMs: 本地播放最小1秒
            (int) maxBufferMs,       // maxBufferMs: 最大缓冲时间
            (int) maxBufferMs,       // maxBufferForLocalPlaybackMs: 本地播放最大缓冲时间
            500,                     // bufferForPlaybackMs: 500ms即可开始
            500,                     // bufferForPlaybackForLocalPlaybackMs: 本地播放500ms开始
            1000,                    // bufferForPlaybackAfterRebufferMs: 重新缓冲1秒
            1000,                    // bufferForPlaybackAfterRebufferForLocalPlaybackMs: 本地重新缓冲1秒
            DEFAULT_TARGET_BUFFER_BYTES,  // targetBufferBytes: 默认目标缓冲字节
            true,                    // prioritizeTimeOverSizeThresholds: 优先时间而非大小
            true,                    // prioritizeTimeOverSizeThresholdsForLocalPlayback: 本地播放优先时间
            DEFAULT_BACK_BUFFER_DURATION_MS,  // backBufferDurationMs: 默认后向缓冲时长
            DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME,  // retainBackBufferFromKeyframe: 默认保留关键帧
            new HashMap<>()          // playerTargetBufferBytes: 空的播放器目标缓冲字节映射
        );
        this.maxBufferMs = maxBufferMs;
    }

    /**
     * 核心方法：决定是否继续加载数据
     * 只要缓冲时长未达到 maxBufferMs（1分钟），就继续加载
     */
    @Override
    public boolean shouldContinueLoading(
            long playbackPositionUs,
            long bufferedDurationUs,
            float playbackSpeed) {
        long bufferedMs = bufferedDurationUs / 1000;
        return bufferedMs < maxBufferMs;
    }
}
