package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.bean.DanmakuAnime;
import com.github.tvbox.osc.bean.DanmakuEpisode;
import com.github.tvbox.osc.databinding.DialogDanmakuSearchBinding;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.DanmakuAnimeAdapter;
import com.github.tvbox.osc.ui.adapter.DanmakuEpisodeAdapter;
import com.github.tvbox.osc.utils.Notify;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.regex.Pattern;

public class DanmakuSearchDialog extends BaseDialog {

    private DialogDanmakuSearchBinding binding;
    private DanmakuAnimeAdapter animeAdapter;
    private DanmakuEpisodeAdapter episodeAdapter;
    private String videoTitle;
    private String episodeName;
    private int episodeIndex = -1;
    private DanmakuAnime selectedAnime;
    private List<DanmakuEpisode> currentEpisodes;
    private boolean isReversed = false;

    // 集数标识的正则表达式
    private static final Pattern EPISODE_PATTERN = Pattern.compile("第\\d+集|\\d+集|EP?\\d+|S\\d+E\\d+|\\d{8}|\\d{4}-\\d{2}-\\d{2}", Pattern.CASE_INSENSITIVE);
    // 从标题中提取集数的正则表达式
    private static final Pattern EPISODE_NUMBER_PATTERN = Pattern.compile("第(\\d+)[话集]|EP?(\\d+)|S\\d+E(\\d+)", Pattern.CASE_INSENSITIVE);

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    public DanmakuSearchDialog videoTitle(String title) {
        this.videoTitle = title;
        return this;
    }

    public DanmakuSearchDialog episodeName(String name) {
        this.episodeName = name;
        return this;
    }

    public DanmakuSearchDialog episodeIndex(int index) {
        this.episodeIndex = index;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuSearchBinding.inflate(inflater, container, false);
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof BottomSheetDialogFragment) return;
        }
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected boolean transparent() {
        return false;
    }

    @Override
    protected void initView() {
        // 设置标题
        if (!TextUtils.isEmpty(videoTitle)) {
            binding.title.setText(cleanTitle(videoTitle));
        }

        // 初始化弹幕源列表
        binding.animeList.setLayoutManager(new LinearLayoutManager(getContext()));
        animeAdapter = new DanmakuAnimeAdapter(this::onAnimeClick);
        binding.animeList.setAdapter(animeAdapter);

        // 初始化剧集列表
        binding.episodeList.setLayoutManager(new LinearLayoutManager(getContext()));
        episodeAdapter = new DanmakuEpisodeAdapter(this::onEpisodeClick);
        binding.episodeList.setAdapter(episodeAdapter);

        // 设置搜索输入框的初始值
        if (!TextUtils.isEmpty(videoTitle)) {
            binding.searchInput.setText(cleanTitle(videoTitle));
            binding.searchInput.setSelection(binding.searchInput.getText().length());
        }

        // 自动搜索
        if (!TextUtils.isEmpty(videoTitle)) {
            searchAnime(cleanTitle(videoTitle));
        }
    }

    @Override
    protected void initEvent() {
        // 关闭按钮点击
        binding.close.setOnClickListener(v -> dismiss());

        // 搜索输入框
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        // 搜索按钮
        binding.searchButton.setOnClickListener(v -> performSearch());

        // 反转按钮
        binding.reverseButton.setOnClickListener(v -> reverseEpisodes());

        // 快速跳转按钮
        binding.jumpButton.setOnClickListener(v -> showJumpDialog());
    }

    /**
     * 清理标题，移除集数标识
     */
    private String cleanTitle(String title) {
        if (TextUtils.isEmpty(title)) return "";
        return EPISODE_PATTERN.matcher(title).replaceAll("").trim();
    }

    /**
     * 执行搜索
     */
    private void performSearch() {
        String keyword = binding.searchInput.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            Notify.show("请输入搜索关键词");
            return;
        }
        searchAnime(keyword);
    }

    /**
     * 搜索番剧
     */
    private void searchAnime(String keyword) {
        showLoading();
        hideEpisodeList();

        DanmakuApi.searchAnime(keyword, new DanmakuApi.DanmakuCallback<List<DanmakuAnime>>() {
            @Override
            public void onSuccess(List<DanmakuAnime> data) {
                App.post(() -> {
                    hideLoading();
                    if (data == null || data.isEmpty()) {
                        Notify.show("未找到相关番剧");
                        return;
                    }
                    showAnimeList(data);
                });
            }

            @Override
            public void onError(String error) {
                App.post(() -> {
                    hideLoading();
                    Notify.show("搜索失败: " + error);
                });
            }
        });
    }

    /**
     * 显示番剧列表
     */
    private void showAnimeList(List<DanmakuAnime> animes) {
        animeAdapter.setData(animes);
        binding.animeList.setVisibility(View.VISIBLE);
        binding.quickActionLayout.setVisibility(View.GONE);
    }

    /**
     * 番剧点击事件
     */
    private void onAnimeClick(DanmakuAnime anime) {
        selectedAnime = anime;
        loadEpisodes(anime.getAnimeId());
    }

    /**
     * 加载剧集列表
     */
    private void loadEpisodes(int animeId) {
        showLoading();
        hideAnimeList();

        DanmakuApi.getBangumiEpisodes(animeId, new DanmakuApi.DanmakuCallback<List<DanmakuEpisode>>() {
            @Override
            public void onSuccess(List<DanmakuEpisode> data) {
                App.post(() -> {
                    hideLoading();
                    if (data == null || data.isEmpty()) {
                        Notify.show("该番剧暂无剧集");
                        showAnimeList(animeAdapter.getData());
                        return;
                    }
                    showEpisodeList(data);
                });
            }

            @Override
            public void onError(String error) {
                App.post(() -> {
                    hideLoading();
                    Notify.show("获取剧集失败: " + error);
                    showAnimeList(animeAdapter.getData());
                });
            }
        });
    }

    /**
     * 显示剧集列表
     */
    private void showEpisodeList(List<DanmakuEpisode> episodes) {
        currentEpisodes = episodes;
        episodeAdapter.setData(episodes);
        binding.episodeList.setVisibility(View.VISIBLE);
        binding.quickActionLayout.setVisibility(View.VISIBLE);

        // 自动匹配并滚动到对应集数
        final int matchedPosition = autoMatchEpisode(episodes);

        if (matchedPosition >= 0) {
            binding.episodeList.postDelayed(() -> {
                binding.episodeList.scrollToPosition(matchedPosition);
                Notify.show("已自动定位到: " + episodes.get(matchedPosition).getDisplayName());
            }, 200);
        }
    }

    /**
     * 剧集点击事件
     */
    private void onEpisodeClick(DanmakuEpisode episode) {
        String danmakuUrl = DanmakuApi.getDanmakuUrl(episode.getEpisodeId());
        Notify.show("正在加载弹幕: " + episode.getDisplayName());

        // 通知VideoActivity加载弹幕
        RefreshEvent.danmaku(danmakuUrl);

        dismiss();
    }

    /**
     * 显示加载中
     */
    private void showLoading() {
        binding.loading.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏加载中
     */
    private void hideLoading() {
        binding.loading.setVisibility(View.GONE);
    }

    /**
     * 隐藏番剧列表
     */
    private void hideAnimeList() {
        binding.animeList.setVisibility(View.GONE);
    }

    /**
     * 隐藏剧集列表
     */
    private void hideEpisodeList() {
        binding.episodeList.setVisibility(View.GONE);
        binding.quickActionLayout.setVisibility(View.GONE);
    }

    /**
     * 自动匹配剧集
     */
    private int autoMatchEpisode(List<DanmakuEpisode> episodes) {
        if (episodes == null || episodes.isEmpty()) return -1;

        // 规则0: 从episodeName中提取序号前缀（格式：[序号]原名称）
        int prefixNumber = extractPrefixNumber(episodeName);
        if (prefixNumber > 0) {
            for (int i = 0; i < episodes.size(); i++) {
                DanmakuEpisode ep = episodes.get(i);

                // 先尝试使用接口返回的episodeNumber
                String epNumber = ep.getEpisodeNumber();
                if (!TextUtils.isEmpty(epNumber)) {
                    try {
                        int epNum = Integer.parseInt(epNumber);
                        if (epNum == prefixNumber) {
                            return i;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }

                // 再尝试从标题中解析集数
                String title = ep.getEpisodeTitle();
                if (!TextUtils.isEmpty(title)) {
                    int parsedNumber = parseEpisodeNumber(title);
                    if (parsedNumber == prefixNumber) {
                        return i;
                    }
                }
            }
        }

        // 规则1: 精准匹配剧集名
        if (!TextUtils.isEmpty(episodeName)) {
            for (int i = 0; i < episodes.size(); i++) {
                DanmakuEpisode ep = episodes.get(i);
                String epTitle = ep.getEpisodeTitle();
                String epDisplay = ep.getDisplayName();

                if (episodeName.equals(epTitle) || episodeName.equals(epDisplay)) {
                    return i;
                }
            }
        }

        // 规则2: 使用剧集索引匹配
        if (episodeIndex >= 0) {
            int targetNumber = episodeIndex + 1;

            for (int i = 0; i < episodes.size(); i++) {
                DanmakuEpisode ep = episodes.get(i);

                // 先尝试使用接口返回的episodeNumber
                String epNumber = ep.getEpisodeNumber();
                if (!TextUtils.isEmpty(epNumber)) {
                    try {
                        int epNum = Integer.parseInt(epNumber);
                        if (epNum == targetNumber) {
                            return i;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }

                // 再尝试从标题中解析集数
                String title = ep.getEpisodeTitle();
                if (!TextUtils.isEmpty(title)) {
                    int parsedNumber = parseEpisodeNumber(title);
                    if (parsedNumber == targetNumber) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    /**
     * 从episodeName中提取序号前缀
     */
    private int extractPrefixNumber(String name) {
        if (TextUtils.isEmpty(name)) return -1;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\[(\\d+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(name);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return -1;
    }

    /**
     * 从标题中解析集数
     */
    private int parseEpisodeNumber(String title) {
        java.util.regex.Matcher matcher = EPISODE_NUMBER_PATTERN.matcher(title);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null) {
                    try {
                        return Integer.parseInt(group);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return -1;
    }

    /**
     * 反转剧集列表
     */
    private void reverseEpisodes() {
        if (currentEpisodes == null || currentEpisodes.isEmpty()) {
            Notify.show("没有可反转的剧集");
            return;
        }

        isReversed = !isReversed;
        java.util.Collections.reverse(currentEpisodes);
        episodeAdapter.setData(currentEpisodes);

        // 反转后滚动到第一个剧集
        binding.episodeList.scrollToPosition(0);

        Notify.show(isReversed ? "已反转剧集顺序" : "已恢复原始顺序");
    }

    /**
     * 显示快速跳转对话框
     */
    private void showJumpDialog() {
        if (currentEpisodes == null || currentEpisodes.isEmpty()) {
            Notify.show("没有可跳转的剧集");
            return;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
        builder.setTitle("快速跳转");

        final android.widget.EditText input = new android.widget.EditText(getActivity());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("请输入集数 (1-" + currentEpisodes.size() + ")");
        builder.setView(input);

        builder.setPositiveButton("跳转", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Notify.show("请输入集数");
                return;
            }

            try {
                int targetEpisode = Integer.parseInt(text);
                if (targetEpisode < 1 || targetEpisode > currentEpisodes.size()) {
                    Notify.show("集数超出范围 (1-" + currentEpisodes.size() + ")");
                    return;
                }

                int position = targetEpisode - 1;
                binding.episodeList.scrollToPosition(position);
                Notify.show("已跳转到: " + currentEpisodes.get(position).getDisplayName());
            } catch (NumberFormatException e) {
                Notify.show("请输入有效的数字");
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // 清理资源
        if (animeAdapter != null) animeAdapter.clear();
        if (episodeAdapter != null) episodeAdapter.clear();
        currentEpisodes = null;
        isReversed = false;
    }
}
