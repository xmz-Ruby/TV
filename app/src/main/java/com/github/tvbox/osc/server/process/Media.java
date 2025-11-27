package com.github.tvbox.osc.server.process;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import com.github.tvbox.osc.player.Players;
import com.github.tvbox.osc.server.Nano;
import com.github.tvbox.osc.server.Server;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoHTTPD;

public class Media implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String path) {
        return "/media".equals(path);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String path, Map<String, String> files) {
        JsonObject result = new JsonObject();

        // 检查是否正在投屏
        if (Server.get().isCasting()) {
            // 投屏模式：返回投屏信息
            result.addProperty("url", Server.get().getCastUrl());
            result.addProperty("state", PlaybackStateCompat.STATE_PLAYING); // 假设投屏时是播放状态
            result.addProperty("speed", 1.0f);
            result.addProperty("title", Server.get().getCurrentTitle());
            result.addProperty("artist", Server.get().getCurrentEpisode());
            result.addProperty("artwork", "");
            result.addProperty("duration", Server.get().getCastDuration());
            result.addProperty("position", Server.get().getCastPosition());
            result.addProperty("currentTitle", Server.get().getCurrentTitle());
            result.addProperty("currentEpisode", Server.get().getCurrentEpisode());
            result.addProperty("isCasting", true);
        } else if (isNull()) {
            // 没有播放器且没有投屏：返回空数据
            return Nano.success("{}");
        } else {
            // 本地播放模式：返回播放器信息
            result.addProperty("url", getUrl());
            result.addProperty("state", getState());
            result.addProperty("speed", getSpeed());
            result.addProperty("title", getTitle());
            result.addProperty("artist", getArtist());
            result.addProperty("artwork", getArtUri());
            result.addProperty("duration", getDuration());
            result.addProperty("position", getPosition());
            result.addProperty("currentTitle", Server.get().getCurrentTitle());
            result.addProperty("currentEpisode", Server.get().getCurrentEpisode());
            result.addProperty("isCasting", false);
        }

        return Nano.success(result.toString());
    }

    private Players getPlayer() {
        return Server.get().getPlayer();
    }

    private boolean isNull() {
        return Objects.isNull(getPlayer()) || Objects.isNull(getPlayer().getSession());
    }

    private PlaybackStateCompat getPlaybackState() {
        return getPlayer().getSession().getController().getPlaybackState();
    }

    private MediaMetadataCompat getMetadata() {
        return getPlayer().getSession().getController().getMetadata();
    }

    private String getUrl() {
        return TextUtils.isEmpty(getPlayer().getUrl()) ? "" : getPlayer().getUrl();
    }

    private String getTitle() {
        return getMetadata() == null || getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE).isEmpty() ? "" : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE);
    }

    private String getArtist() {
        return getMetadata() == null || getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST).isEmpty() ? "" : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
    }

    private String getArtUri() {
        return getMetadata() == null ? "" : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
    }

    private long getDuration() {
        return getMetadata() == null ? -1 : getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
    }

    private int getState() {
        return getPlaybackState() == null ? -1 : getPlaybackState().getState();
    }

    private long getPosition() {
        return getPlaybackState() == null ? -1 : getPlaybackState().getPosition();
    }

    private float getSpeed() {
        return getPlaybackState() == null ? -1 : getPlaybackState().getPlaybackSpeed();
    }
}