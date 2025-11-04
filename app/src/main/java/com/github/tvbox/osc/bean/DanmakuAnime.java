package com.github.tvbox.osc.bean;

import com.google.gson.annotations.SerializedName;

public class DanmakuAnime {

    @SerializedName("animeId")
    private int animeId;

    @SerializedName("animeTitle")
    private String animeTitle;

    @SerializedName("type")
    private String type;

    @SerializedName("episodeCount")
    private int episodeCount;

    public int getAnimeId() {
        return animeId;
    }

    public void setAnimeId(int animeId) {
        this.animeId = animeId;
    }

    public String getAnimeTitle() {
        return animeTitle;
    }

    public void setAnimeTitle(String animeTitle) {
        this.animeTitle = animeTitle;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getEpisodeCount() {
        return episodeCount;
    }

    public void setEpisodeCount(int episodeCount) {
        this.episodeCount = episodeCount;
    }
}
