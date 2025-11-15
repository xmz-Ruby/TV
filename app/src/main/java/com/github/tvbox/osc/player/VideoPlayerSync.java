package com.github.tvbox.osc.player;

import master.flame.danmaku.danmaku.model.AbsDanmakuSync;

/**
 * 视频播放器与弹幕的时间同步器
 * 用于解决弹幕播放进度与视频播放进度不同步的问题
 */
public class VideoPlayerSync extends AbsDanmakuSync {

    private Players player;

    public VideoPlayerSync(Players player) {
        this.player = player;
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
     */
    @Override
    public int getSyncState() {
        if (player == null) {
            return SYNC_STATE_HALT;
        }
        return player.isPlaying() ? SYNC_STATE_PLAYING : SYNC_STATE_HALT;
    }

    /**
     * 设置时间差阈值（毫秒）
     * 当弹幕时间与视频时间差超过此值时，会强制同步
     * 默认1500ms，可以根据需要调整
     *
     * 降低此值可以提高同步精度，但可能导致频繁的时间跳跃
     * 增加此值可以减少跳跃，但同步精度会降低
     */
    @Override
    public long getThresholdTimeMills() {
        // 设置为800ms，比默认的1500ms更敏感，能更快地纠正时间偏差
        return 800L;
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
