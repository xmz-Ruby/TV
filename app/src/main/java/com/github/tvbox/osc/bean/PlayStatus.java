package com.github.tvbox.osc.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.api.config.VodConfig;
import com.github.tvbox.osc.db.AppDatabase;

@Entity
public class PlayStatus {

    @NonNull
    @PrimaryKey
    private String vodName; // 剧名
    private String vodId; // 视频ID
    private String sourceKey; // 源key
    private String flagName; // 线路名称
    private int qualityIndex; // 画质索引
    private String episodeName; // 当前集数名称
    private String episodeUrl; // 当前集数URL
    private long position; // 播放进度
    private long updateTime; // 更新时间

    @NonNull
    public String getVodName() {
        return vodName;
    }

    public void setVodName(@NonNull String vodName) {
        this.vodName = vodName;
    }

    public String getVodId() {
        return vodId;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getFlagName() {
        return flagName;
    }

    public void setFlagName(String flagName) {
        this.flagName = flagName;
    }

    public int getQualityIndex() {
        return qualityIndex;
    }

    public void setQualityIndex(int qualityIndex) {
        this.qualityIndex = qualityIndex;
    }

    public String getEpisodeName() {
        return episodeName == null ? "" : episodeName;
    }

    public void setEpisodeName(String episodeName) {
        this.episodeName = episodeName;
    }

    public String getEpisodeUrl() {
        return episodeUrl == null ? "" : episodeUrl;
    }

    public void setEpisodeUrl(String episodeUrl) {
        this.episodeUrl = episodeUrl;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public static PlayStatus find(String vodName) {
        return AppDatabase.get().getPlayStatusDao().find(vodName);
    }

    public PlayStatus save() {
        AppDatabase.get().getPlayStatusDao().insertOrUpdate(this);
        return this;
    }

    public static PlayStatus create(String vodName) {
        PlayStatus status = new PlayStatus();
        status.setVodName(vodName);
        status.setUpdateTime(System.currentTimeMillis());
        return status;
    }
}
