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
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.viewbinding.ViewBinding;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.bean.DanmakuAnime;
import com.github.tvbox.osc.bean.DanmakuEpisode;
import com.github.tvbox.osc.databinding.DialogDanmakuSearchBinding;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.presenter.DanmakuAnimePresenter;
import com.github.tvbox.osc.ui.presenter.DanmakuEpisodePresenter;
import com.github.tvbox.osc.utils.Notify;
import com.github.tvbox.osc.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.regex.Pattern;

public class DanmakuSearchDialog extends BaseDialog {

    private DialogDanmakuSearchBinding binding;
    private ArrayObjectAdapter animeAdapter;
    private ArrayObjectAdapter episodeAdapter;
    private DanmakuAnimePresenter animePresenter;
    private DanmakuEpisodePresenter episodePresenter;
    private String videoTitle;
    private DanmakuAnime selectedAnime;

    // 集数标识的正则表达式
    private static final Pattern EPISODE_PATTERN = Pattern.compile("第\\d+集|\\d+集|EP?\\d+|S\\d+E\\d+|\\d{8}|\\d{4}-\\d{2}-\\d{2}", Pattern.CASE_INSENSITIVE);

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    public DanmakuSearchDialog videoTitle(String title) {
        this.videoTitle = title;
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
    protected void initView() {
        // 设置标题
        if (!TextUtils.isEmpty(videoTitle)) {
            binding.title.setText(cleanTitle(videoTitle));
        }

        // 初始化弹幕源列表
        binding.animeList.setVerticalSpacing(ResUtil.dp2px(8));
        binding.animeList.setAdapter(new ItemBridgeAdapter(animeAdapter = new ArrayObjectAdapter(animePresenter = new DanmakuAnimePresenter(this::onAnimeClick))));

        // 初始化剧集列表
        binding.episodeList.setVerticalSpacing(ResUtil.dp2px(8));
        binding.episodeList.setAdapter(new ItemBridgeAdapter(episodeAdapter = new ArrayObjectAdapter(episodePresenter = new DanmakuEpisodePresenter(this::onEpisodeClick))));

        // 自动搜索
        if (!TextUtils.isEmpty(videoTitle)) {
            searchAnime(cleanTitle(videoTitle));
        }
    }

    @Override
    protected void initEvent() {
        // 搜索按钮点击
        binding.search.setOnClickListener(v -> showSearchInput());

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
    }

    /**
     * 清理标题，移除集数标识
     */
    private String cleanTitle(String title) {
        if (TextUtils.isEmpty(title)) return "";
        // 移除集数标识
        return EPISODE_PATTERN.matcher(title).replaceAll("").trim();
    }

    /**
     * 显示搜索输入框
     */
    private void showSearchInput() {
        binding.searchLayout.setVisibility(View.VISIBLE);
        binding.searchInput.setText(cleanTitle(videoTitle));
        binding.searchInput.requestFocus();
        binding.searchInput.selectAll();
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
        binding.searchLayout.setVisibility(View.GONE);
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
        animeAdapter.setItems(animes, null);
        binding.animeList.setVisibility(View.VISIBLE);
        binding.animeList.postDelayed(() -> {
            View firstItem = binding.animeList.getLayoutManager().findViewByPosition(0);
            if (firstItem != null) firstItem.requestFocus();
        }, 100);
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
                        showAnimeList(animeAdapter);
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
                    showAnimeList(animeAdapter);
                });
            }
        });
    }

    /**
     * 显示剧集列表
     */
    private void showEpisodeList(List<DanmakuEpisode> episodes) {
        episodeAdapter.setItems(episodes, null);
        binding.episodeList.setVisibility(View.VISIBLE);
        binding.episodeList.postDelayed(() -> {
            View firstItem = binding.episodeList.getLayoutManager().findViewByPosition(0);
            if (firstItem != null) firstItem.requestFocus();
        }, 100);
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
    }

    /**
     * 显示番剧列表（从Adapter恢复）
     */
    private void showAnimeList(ArrayObjectAdapter adapter) {
        binding.animeList.setVisibility(View.VISIBLE);
        binding.animeList.postDelayed(() -> {
            View firstItem = binding.animeList.getLayoutManager().findViewByPosition(0);
            if (firstItem != null) firstItem.requestFocus();
        }, 100);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // 清理资源
        if (animeAdapter != null) animeAdapter.clear();
        if (episodeAdapter != null) episodeAdapter.clear();
    }
}
