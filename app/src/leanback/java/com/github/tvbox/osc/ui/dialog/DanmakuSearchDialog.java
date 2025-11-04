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
import com.github.tvbox.osc.server.Server;
import com.github.tvbox.osc.ui.presenter.DanmakuAnimePresenter;
import com.github.tvbox.osc.ui.presenter.DanmakuEpisodePresenter;
import com.github.tvbox.osc.utils.Notify;
import com.github.tvbox.osc.utils.QRCode;
import com.github.tvbox.osc.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.net.URLEncoder;

import java.util.List;
import java.util.regex.Pattern;

public class DanmakuSearchDialog extends BaseDialog {

    private DialogDanmakuSearchBinding binding;
    private ArrayObjectAdapter animeAdapter;
    private ArrayObjectAdapter episodeAdapter;
    private DanmakuAnimePresenter animePresenter;
    private DanmakuEpisodePresenter episodePresenter;
    private String videoTitle;
    private String episodeName;
    private int episodeIndex = -1; // 当前播放的集数索引
    private DanmakuAnime selectedAnime;
    private List<DanmakuEpisode> currentEpisodes; // 当前弹幕剧集列表
    private boolean isReversed = false; // 是否已反转

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
        // 设置弹层背景不透明度，使内容更清晰
        setDimAmount(0.7f);

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
        // 标题点击显示二维码
        binding.title.setOnClickListener(v -> toggleQRCode());

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

        // 反转按钮
        binding.reverseButton.setOnClickListener(v -> reverseEpisodes());

        // 快速跳转按钮
        binding.jumpButton.setOnClickListener(v -> showJumpDialog());
    }

    /**
     * 切换二维码显示
     */
    private void toggleQRCode() {
        if (binding.qrcodeLayout.getVisibility() == View.VISIBLE) {
            binding.qrcodeLayout.setVisibility(View.GONE);
        } else {
            showQRCode();
        }
    }

    /**
     * 显示二维码
     */
    private void showQRCode() {
        try {
            String name = URLEncoder.encode(cleanTitle(videoTitle), "UTF-8");
            // 使用当前播放的集名，如果没有则使用视频标题
            String episode = URLEncoder.encode(TextUtils.isEmpty(episodeName) ? videoTitle : episodeName, "UTF-8");
            // 使用局域网IP而非127.0.0.1
            String baseUrl = Server.get().getAddress(false);
            String url = baseUrl + "/danmaku?name=" + name + "&episode=" + episode;

            binding.qrcode.setImageBitmap(QRCode.getBitmap(url, 200, 0));
            binding.qrcodeInfo.setText("扫码进入弹幕投送页面\n" + baseUrl);
            binding.qrcodeLayout.setVisibility(View.VISIBLE);

            // 隐藏列表
            binding.animeList.setVisibility(View.GONE);
            binding.episodeList.setVisibility(View.GONE);
        } catch (Exception e) {
            Notify.show("生成二维码失败");
        }
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
        binding.qrcodeLayout.setVisibility(View.GONE);
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
        currentEpisodes = episodes;
        episodeAdapter.setItems(episodes, null);
        binding.qrcodeLayout.setVisibility(View.GONE);
        binding.episodeList.setVisibility(View.VISIBLE);
        binding.quickActionLayout.setVisibility(View.VISIBLE);

        // 设置快速操作栏按钮的焦点导航
        setupQuickActionFocus();

        // 自动匹配并滚动到对应集数
        final int matchedPosition = autoMatchEpisode(episodes);

        android.util.Log.d("DanmakuSearch", "匹配结果位置: " + matchedPosition);

        // 等待列表渲染完成
        binding.episodeList.postDelayed(() -> {
            if (matchedPosition >= 0) {
                android.util.Log.d("DanmakuSearch", "开始滚动到位置: " + matchedPosition);
                // 自动定位成功，先滚动到匹配位置
                binding.episodeList.setSelectedPosition(matchedPosition);

                // 等待滚动完成后再聚焦
                binding.episodeList.postDelayed(() -> {
                    View matchedItem = binding.episodeList.getLayoutManager().findViewByPosition(matchedPosition);
                    android.util.Log.d("DanmakuSearch", "尝试聚焦到匹配项，view是否为null: " + (matchedItem == null));

                    if (matchedItem != null) {
                        boolean focused = matchedItem.requestFocus();
                        android.util.Log.d("DanmakuSearch", "聚焦结果: " + focused);
                        Notify.show("已自动定位到: " + episodes.get(matchedPosition).getDisplayName());
                    } else {
                        // 如果view还没准备好，再等一会
                        binding.episodeList.postDelayed(() -> {
                            View retryItem = binding.episodeList.getLayoutManager().findViewByPosition(matchedPosition);
                            if (retryItem != null) {
                                retryItem.requestFocus();
                                android.util.Log.d("DanmakuSearch", "重试聚焦成功");
                                Notify.show("已自动定位到: " + episodes.get(matchedPosition).getDisplayName());
                            }
                        }, 100);
                    }
                }, 150);
            } else {
                android.util.Log.d("DanmakuSearch", "未匹配到，聚焦到第一个");
                // 未匹配到，聚焦到第一个
                View firstItem = binding.episodeList.getLayoutManager().findViewByPosition(0);
                if (firstItem != null) firstItem.requestFocus();
            }
        }, 200);
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
        binding.qrcodeLayout.setVisibility(View.GONE);
        binding.animeList.setVisibility(View.VISIBLE);
        binding.animeList.postDelayed(() -> {
            View firstItem = binding.animeList.getLayoutManager().findViewByPosition(0);
            if (firstItem != null) firstItem.requestFocus();
        }, 100);
    }

    /**
     * 设置快速操作栏的焦点导航
     */
    private void setupQuickActionFocus() {
        // 设置快速操作栏按钮可以向上导航到标题栏
        binding.reverseButton.setNextFocusUpId(R.id.search);
        binding.jumpButton.setNextFocusUpId(R.id.search);

        // 设置标题栏按钮可以向下导航到快速操作栏
        binding.title.setNextFocusDownId(R.id.reverseButton);
        binding.search.setNextFocusDownId(R.id.reverseButton);
        binding.close.setNextFocusDownId(R.id.reverseButton);
    }

    /**
     * 自动匹配剧集
     * @param episodes 弹幕剧集列表
     * @return 匹配到的位置，-1表示未匹配
     */
    private int autoMatchEpisode(List<DanmakuEpisode> episodes) {
        if (episodes == null || episodes.isEmpty()) return -1;

        android.util.Log.d("DanmakuSearch", "开始自动匹配 - episodeName: " + episodeName + ", episodeIndex: " + episodeIndex);

        // 规则0: 从episodeName中提取序号前缀（格式：[序号]原名称）
        int prefixNumber = extractPrefixNumber(episodeName);
        if (prefixNumber > 0) {
            android.util.Log.d("DanmakuSearch", "从episodeName提取到序号前缀: " + prefixNumber);
            // 使用提取的序号进行匹配
            for (int i = 0; i < episodes.size(); i++) {
                DanmakuEpisode ep = episodes.get(i);

                // 先尝试使用接口返回的episodeNumber
                String epNumber = ep.getEpisodeNumber();
                if (!TextUtils.isEmpty(epNumber)) {
                    try {
                        int epNum = Integer.parseInt(epNumber);
                        if (epNum == prefixNumber) {
                            android.util.Log.d("DanmakuSearch", "序号前缀匹配成功(episodeNumber) - 位置: " + i);
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
                        android.util.Log.d("DanmakuSearch", "序号前缀匹配成功(标题解析) - 位置: " + i);
                        return i;
                    }
                }
            }
            android.util.Log.d("DanmakuSearch", "序号前缀匹配失败");
        }

        // 规则1: 精准匹配剧集名
        if (!TextUtils.isEmpty(episodeName)) {
            for (int i = 0; i < episodes.size(); i++) {
                DanmakuEpisode ep = episodes.get(i);
                String epTitle = ep.getEpisodeTitle();
                String epDisplay = ep.getDisplayName();

                android.util.Log.d("DanmakuSearch", "检查剧集[" + i + "] - title: " + epTitle + ", display: " + epDisplay);

                if (episodeName.equals(epTitle) || episodeName.equals(epDisplay)) {
                    android.util.Log.d("DanmakuSearch", "精准匹配成功 - 位置: " + i);
                    return i;
                }
            }
            android.util.Log.d("DanmakuSearch", "精准匹配失败，尝试索引匹配");
        }

        // 规则2: 使用剧集索引匹配（episodeIndex从0开始，对应第1集）
        if (episodeIndex >= 0) {
            int targetNumber = episodeIndex + 1; // 转换为集数（第1集、第2集...）
            android.util.Log.d("DanmakuSearch", "目标集数: " + targetNumber);

            for (int i = 0; i < episodes.size(); i++) {
                DanmakuEpisode ep = episodes.get(i);

                // 先尝试使用接口返回的episodeNumber
                String epNumber = ep.getEpisodeNumber();
                if (!TextUtils.isEmpty(epNumber)) {
                    try {
                        int epNum = Integer.parseInt(epNumber);
                        android.util.Log.d("DanmakuSearch", "检查episodeNumber[" + i + "]: " + epNum);
                        if (epNum == targetNumber) {
                            android.util.Log.d("DanmakuSearch", "episodeNumber匹配成功 - 位置: " + i);
                            return i;
                        }
                    } catch (NumberFormatException e) {
                        android.util.Log.d("DanmakuSearch", "episodeNumber解析失败: " + epNumber);
                    }
                }

                // 再尝试从标题中解析集数
                String title = ep.getEpisodeTitle();
                if (!TextUtils.isEmpty(title)) {
                    int parsedNumber = parseEpisodeNumber(title);
                    if (parsedNumber > 0) {
                        android.util.Log.d("DanmakuSearch", "从标题解析集数[" + i + "]: " + parsedNumber);
                        if (parsedNumber == targetNumber) {
                            android.util.Log.d("DanmakuSearch", "标题解析匹配成功 - 位置: " + i);
                            return i;
                        }
                    }
                }
            }
            android.util.Log.d("DanmakuSearch", "索引匹配失败");
        }

        android.util.Log.d("DanmakuSearch", "所有匹配规则均失败");
        return -1; // 未匹配到
    }

    /**
     * 从episodeName中提取序号前缀
     * @param name 剧集名称，格式：[序号]原名称
     * @return 序号，-1表示没有序号前缀
     */
    private int extractPrefixNumber(String name) {
        if (TextUtils.isEmpty(name)) return -1;

        // 匹配格式：[数字]
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
     * @param title 标题
     * @return 集数，-1表示解析失败
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
        episodeAdapter.setItems(currentEpisodes, null);

        // 反转后聚焦到第一个剧集
        binding.episodeList.postDelayed(() -> {
            binding.episodeList.setSelectedPosition(0);
            binding.episodeList.postDelayed(() -> {
                View firstItem = binding.episodeList.getLayoutManager().findViewByPosition(0);
                if (firstItem != null) firstItem.requestFocus();
            }, 50);
        }, 100);

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

        // 创建输入对话框
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
                // 先滚动到目标位置
                binding.episodeList.setSelectedPosition(position);
                // 延迟聚焦，确保视图已经渲染
                binding.episodeList.postDelayed(() -> {
                    View targetItem = binding.episodeList.getLayoutManager().findViewByPosition(position);
                    if (targetItem != null) {
                        targetItem.requestFocus();
                        Notify.show("已跳转到: " + currentEpisodes.get(position).getDisplayName());
                    }
                }, 150);
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
