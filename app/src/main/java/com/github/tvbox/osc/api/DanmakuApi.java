package com.github.tvbox.osc.api;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.bean.DanmakuAnime;
import com.github.tvbox.osc.bean.DanmakuEpisode;
import com.github.tvbox.osc.Setting;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class DanmakuApi {

    private static final Gson gson = new Gson();

    private static String getBaseUrl() {
        String host = Setting.getDanmuHost();
        return TextUtils.isEmpty(host) ? "" : host;
    }

    public interface DanmakuCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    /**
     * 搜索番剧
     * @param keyword 搜索关键词
     * @param callback 回调
     */
    public static void searchAnime(String keyword, DanmakuCallback<List<DanmakuAnime>> callback) {
        try {
            String url = getBaseUrl() + "/search/anime?keyword=" + URLEncoder.encode(keyword, "UTF-8");
            OkHttp.newCall(url).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.onError("网络请求失败: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JsonObject json = gson.fromJson(body, JsonObject.class);

                        if (!json.has("success") || !json.get("success").getAsBoolean()) {
                            callback.onError("搜索失败");
                            return;
                        }

                        List<DanmakuAnime> animes = new ArrayList<>();
                        if (json.has("animes")) {
                            JsonArray animesArray = json.getAsJsonArray("animes");
                            for (int i = 0; i < animesArray.size(); i++) {
                                DanmakuAnime anime = gson.fromJson(animesArray.get(i), DanmakuAnime.class);
                                animes.add(anime);
                            }
                        }
                        callback.onSuccess(animes);
                    } catch (Exception e) {
                        callback.onError("解析数据失败: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("请求失败: " + e.getMessage());
        }
    }

    /**
     * 获取番剧的剧集列表
     * @param animeId 番剧ID
     * @param callback 回调
     */
    public static void getBangumiEpisodes(int animeId, DanmakuCallback<List<DanmakuEpisode>> callback) {
        String url = getBaseUrl() + "/bangumi/" + animeId;
        OkHttp.newCall(url).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError("网络请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = gson.fromJson(body, JsonObject.class);

                    if (!json.has("success") || !json.get("success").getAsBoolean()) {
                        callback.onError("获取剧集失败");
                        return;
                    }

                    List<DanmakuEpisode> episodes = new ArrayList<>();
                    if (json.has("bangumi")) {
                        JsonObject bangumi = json.getAsJsonObject("bangumi");
                        if (bangumi.has("episodes")) {
                            JsonArray episodesArray = bangumi.getAsJsonArray("episodes");
                            for (int i = 0; i < episodesArray.size(); i++) {
                                DanmakuEpisode episode = gson.fromJson(episodesArray.get(i), DanmakuEpisode.class);
                                episodes.add(episode);
                            }
                        }
                    }
                    callback.onSuccess(episodes);
                } catch (Exception e) {
                    callback.onError("解析数据失败: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 获取弹幕URL
     * @param episodeId 剧集ID
     * @return 弹幕XML的URL
     */
    public static String getDanmakuUrl(int episodeId) {
        return getBaseUrl() + "/comment/" + episodeId + "?format=xml";
    }
}
