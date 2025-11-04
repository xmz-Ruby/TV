package com.github.tvbox.osc.bean;

import com.google.gson.annotations.SerializedName;

public class DanmakuEpisode {

    @SerializedName("episodeId")
    private int episodeId;

    @SerializedName("episodeTitle")
    private String episodeTitle;

    @SerializedName("episodeNumber")
    private String episodeNumber;

    private boolean activated;

    public int getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(int episodeId) {
        this.episodeId = episodeId;
    }

    public String getEpisodeTitle() {
        return episodeTitle;
    }

    public void setEpisodeTitle(String episodeTitle) {
        this.episodeTitle = episodeTitle;
    }

    public String getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(String episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public String getDisplayName() {
        if (episodeTitle != null && !episodeTitle.isEmpty()) {
            return episodeTitle;
        }
        return "第" + episodeNumber + "集";
    }
}
