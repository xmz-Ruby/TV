package com.github.tvbox.osc.player;

import master.flame.danmaku.danmaku.model.AbsDanmakuSync;

/**
 * 视频播放器与弹幕的时间同步器
 * 用于解决弹幕播放进度与视频播放进度不同步的问题
 */
public class VideoPlayerSync extends AbsDanmakuSync {

    private Players player;
    private long lastSeekTime = 0;
    private static final long SEEK_GRACE_PERIOD_MS = 500; // seek后500ms内保持播放状态

    public VideoPlayerSync(Players player) {
        this.player = player;
    }

    /**
     * 标记发生了seek操作，在seek后的一段时间内保持弹幕播放状态
     * 解决拖动进度条后弹幕消失的问题
     */
    public void markSeek() {
        this.lastSeekTime = System.currentTimeMillis();
    }

    /**
     * 获取视频播放器的当前播放时间
     * 这是弹幕同步的核心方法，确保弹幕时间始终与视频时间一致
     */
    @Override
    public long getUptimeMillis() {
        if (player == null) {
            return 0;
        }
        return player.getPosition();
    }

    /**
     * 获取播放器的同步状态
     * SYNC_STATE_PLAYING: 正在播放，弹幕应该继续
     * SYNC_STATE_HALT: 暂停状态，弹幕应该暂停
     *
     * 在seek后的短时间内，返回SYNC_STATE_PLAYING以避免弹幕被暂停
     */
    @Override
    public int getSyncState() {
        if (player == null) {
            return SYNC_STATE_HALT;
        }
        // 如果在seek后的宽限期内，返回播放状态以保持弹幕显示
        if (System.currentTimeMillis() - lastSeekTime < SEEK_GRACE_PERIOD_MS) {
            return SYNC_STATE_PLAYING;
        }
        return player.isPlaying() ? SYNC_STATE_PLAYING : SYNC_STATE_HALT;
    }

    /**
     * 设置时间差阈值（毫秒）
     * 当弹幕时间与视频时间差超过此值时，会强制同步
     * 默认1500ms，可以根据需要调整
     *
     * 降低此值可以提高同步精度，确保弹幕始终跟随视频
     * 由于已经启用了绘制缓存，频繁同步不会导致卡顿
     */
    @Override
    public long getThresholdTimeMills() {
        // 设置为500ms，确保弹幕与视频时间紧密同步
        // 当弹幕时间偏离视频时间超过0.5秒时立即纠正
        // 配合绘制缓存，可以保持流畅度
        return 500L;
    }

    /**
     * 是否同步播放/暂停状态
     * 返回true时，弹幕会自动跟随视频的播放/暂停状态
     */
    @Override
    public boolean isSyncPlayingState() {
        return true;
    }
}
