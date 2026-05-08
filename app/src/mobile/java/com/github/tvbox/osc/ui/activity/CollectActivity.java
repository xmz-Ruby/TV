package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.Constant;
import com.github.tvbox.osc.Product;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.api.config.VodConfig;
import com.github.tvbox.osc.bean.Collect;
import com.github.tvbox.osc.bean.Hot;
import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.bean.Site;
import com.github.tvbox.osc.bean.Suggest;
import com.github.tvbox.osc.bean.SuggestTwo;
import com.github.tvbox.osc.bean.Vod;
import com.github.tvbox.osc.databinding.ActivityCollectBinding;
import com.github.tvbox.osc.impl.Callback;
import com.github.tvbox.osc.impl.SiteCallback;
import com.github.tvbox.osc.model.SiteViewModel;
import com.github.tvbox.osc.ui.adapter.CollectAdapter;
import com.github.tvbox.osc.ui.adapter.RecordAdapter;
import com.github.tvbox.osc.ui.adapter.SearchAdapter;
import com.github.tvbox.osc.ui.adapter.VodAdapter;
import com.github.tvbox.osc.ui.adapter.WordAdapter;
import com.github.tvbox.osc.ui.base.BaseActivity;
import com.github.tvbox.osc.ui.base.ViewType;
import com.github.tvbox.osc.ui.custom.CustomScroller;
import com.github.tvbox.osc.ui.custom.CustomTextListener;
import com.github.tvbox.osc.ui.dialog.SiteDialog;
import com.github.tvbox.osc.api.loader.BaseLoader;
import com.github.tvbox.osc.utils.PauseExecutor;
import com.github.tvbox.osc.utils.ResUtil;
import com.github.tvbox.osc.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Response;

public class CollectActivity extends BaseActivity implements CustomScroller.Callback, SiteCallback, WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CollectAdapter.OnClickListener, VodAdapter.OnClickListener {

    private ActivityCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private PauseExecutor mExecutor;
    private List<Site> mSites;
    private int mSearchToken;
    private int mSearchSuccessCount;
    private int mSearchFailureCount;
    private int mSearchTotalCount;

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mScroller = new CustomScroller(this);
        mSites = new ArrayList<>();
        setRecyclerView();
        setViewModel();
        checkKeyword();
        setViewType();
        setSite();
        getHot();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.site.setOnClickListener(this::onSite);
        mBinding.view.setOnClickListener(this::toggleView);
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) search();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().isEmpty()) getHot();
                else getSuggest(s.toString());
            }
        });
    }

    private void setRecyclerView() {
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
    }

    private void setViewType() {
        setViewType(Setting.getViewType(ViewType.GRID));
    }

    private void setViewType(int viewType) {
        int count = Product.getColumn(this) - 1;
        mSearchAdapter.setViewType(viewType, count);
        mSearchAdapter.setSize(Product.getSpec(this, ResUtil.dp2px(152 + (count) * 16), count));
        ((GridLayoutManager) mBinding.recycler.getLayoutManager()).setSpanCount(mSearchAdapter.isGrid() ? count : 1);
        mBinding.view.setImageResource(mSearchAdapter.isGrid() ? R.drawable.ic_action_list : R.drawable.ic_action_grid);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(this, result -> {
            boolean same = result.getList().size() > 0 && mCollectAdapter.getActivated().getSite().equals(result.getList().get(0).getSite());
            if (same) {
                int exactMatchCount = countExactMatch(result.getList(), mBinding.keyword.getText().toString().trim());
                mCollectAdapter.getActivated().getList().addAll(result.getList());
                mCollectAdapter.getActivated().setExactMatchCount(mCollectAdapter.getActivated().getExactMatchCount() + exactMatchCount);
                mCollectAdapter.addToAll(result.getList(), exactMatchCount);
                mCollectAdapter.notifyItemChanged(mCollectAdapter.getPosition());
                mSearchAdapter.addAll(result.getList());
            }
            mScroller.endLoading(result);
        });
    }

    private void checkKeyword() {
        if (TextUtils.isEmpty(getKeyword())) mBinding.keyword.requestFocus();
        else setKeyword(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void setSite() {
        for (Site site : VodConfig.get().getSites()) if (site.isSearchable()) mSites.add(site);
        Site home = VodConfig.get().getHome();
        if (!mSites.contains(home)) return;
        mSites.remove(home);
        mSites.add(0, home);
    }

    private void search() {
        if (empty()) return;
        mSearchAdapter.clear();
        mCollectAdapter.clear();
        startSearchProgress();
        Util.hideKeyboard(mBinding.keyword);
        mBinding.site.setVisibility(View.GONE);
        mBinding.agent.setVisibility(View.GONE);
        mBinding.view.setVisibility(View.VISIBLE);
        mBinding.progress.setVisibility(View.VISIBLE);
        mBinding.result.setVisibility(View.VISIBLE);
        stopSearch();
        mExecutor = new PauseExecutor(Constant.THREAD_POOL * 2);
        String keyword = mBinding.keyword.getText().toString().trim();
        int token = ++mSearchToken;
        for (Site site : mSites) mExecutor.execute(() -> search(site, keyword, token));
        App.post(() -> mRecordAdapter.add(keyword), 250);
    }

    private void search(Site site, String keyword, int token) {
        if (site.getApi().contains(".py") && !BaseLoader.get().isPySpiderReady(site.getApi(), site.getExt())) {
            App.post(() -> onSearchFinished(token, keyword, Result.empty(), false));
            return;
        }
        Result result = Result.empty();
        boolean success = false;
        try {
            result = mViewModel.searchResult(site, keyword, false);
            success = true;
        } catch (Throwable ignored) {
        }
        Result finalResult = result;
        boolean finalSuccess = success;
        App.post(() -> onSearchFinished(token, keyword, finalResult, finalSuccess));
    }

    private void getHot() {
        mBinding.word.setText(R.string.search_hot);
        mWordAdapter.addAll(Hot.get(Setting.getHot()));
    }

    private void getSuggest(String text) {
        mBinding.word.setText(R.string.search_suggest);
        mWordAdapter.clear();
        OkHttp.newCall("https://tv.aiseet.atianqi.com/i-tvbin/qtv_video/search/get_search_smart_box?format=json&page_num=0&page_size=20&key=" + URLEncoder.encode(text)).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (mBinding.keyword.getText().toString().trim().isEmpty()) return;
                List<String> items = SuggestTwo.get(response.body().string());
                App.post(() -> mWordAdapter.appendAll(items));
            }
        });
        OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(text)).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (mBinding.keyword.getText().toString().trim().isEmpty()) return;
                List<String> items = Suggest.get(response.body().string());
                App.post(() -> mWordAdapter.appendAll(items), 200);
            }
        });
    }

    private void onSite(View view) {
        Util.hideKeyboard(mBinding.keyword);
        App.post(() -> SiteDialog.create(this).search().show(), 50);
    }

    private void toggleView(View view) {
        setViewType(mSearchAdapter.isGrid() ? ViewType.LIST : ViewType.GRID);
    }

    private void showAgent() {
        mScroller.reset();
        mSearchAdapter.clear();
        mCollectAdapter.clear();
        mBinding.view.setVisibility(View.GONE);
        mBinding.progress.setVisibility(View.GONE);
        mBinding.result.setVisibility(View.GONE);
        mBinding.site.setVisibility(View.VISIBLE);
        mBinding.agent.setVisibility(View.VISIBLE);
        stopSearch();
    }

    private void stopSearch() {
        mSearchToken++;
        if (mExecutor != null) mExecutor.shutdownNow();
        mExecutor = null;
    }

    private void startSearchProgress() {
        mSearchSuccessCount = 0;
        mSearchFailureCount = 0;
        mSearchTotalCount = mSites.size();
        updateSearchProgress();
    }

    private void onSearchFinished(int token, String keyword, Result result, boolean success) {
        if (token != mSearchToken) return;
        if (success) mSearchSuccessCount++;
        else mSearchFailureCount++;
        updateSearchProgress();
        if (!success || result.getList().isEmpty()) return;
        int exactMatchCount = countExactMatch(result.getList(), keyword);
        if (mCollectAdapter.getPosition() == 0) mSearchAdapter.addAll(result.getList());
        mCollectAdapter.add(Collect.create(result.getList(), exactMatchCount));
        mCollectAdapter.addToAll(result.getList(), exactMatchCount);
    }

    private void updateSearchProgress() {
        mBinding.progress.setText(getString(R.string.search_progress, mSearchSuccessCount, mSearchFailureCount, mSearchTotalCount));
    }

    private int countExactMatch(List<Vod> items, String keyword) {
        int count = 0;
        String target = normalizeKeyword(keyword);
        if (target.isEmpty()) return 0;
        for (Vod item : items) if (normalizeKeyword(item.getVodName()).equals(target)) count++;
        return count;
    }

    private String normalizeKeyword(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void setSite(Site item) {
    }

    @Override
    public void onChanged() {
        mSites.clear();
        setSite();
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        search();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.record.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        mBinding.recordRecycler.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        App.post(() -> mBinding.recordRecycler.requestLayout(), 250);
    }

    @Override
    public void onItemClick(int position, Collect item) {
        mBinding.recycler.scrollToPosition(0);
        mCollectAdapter.setActivated(position);
        mSearchAdapter.setAll(item.getList());
        mScroller.setPage(item.getPage());
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isFolder()) FolderActivity.start(this, item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public void onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        if ("all".equals(activated.getSite().getKey())) return;
        mViewModel.searchContent(activated.getSite(), mBinding.keyword.getText().toString(), page);
        activated.setPage(Integer.parseInt(page));
        mScroller.setLoading(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mExecutor != null) mExecutor.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mExecutor != null) mExecutor.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSearch();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.result)) {
            showAgent();
        } else {
            super.onBackPressed();
        }
    }
}
