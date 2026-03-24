package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.Constant;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.config.VodConfig;
import com.github.tvbox.osc.bean.Collect;
import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.bean.Site;
import com.github.tvbox.osc.databinding.ActivityCollectBinding;
import com.github.tvbox.osc.model.SiteViewModel;
import com.github.tvbox.osc.ui.base.BaseActivity;
import com.github.tvbox.osc.ui.fragment.CollectFragment;
import com.github.tvbox.osc.ui.presenter.CollectPresenter;
import com.github.tvbox.osc.utils.PauseExecutor;
import com.github.tvbox.osc.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CollectActivity extends BaseActivity {

    private ActivityCollectBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private PauseExecutor mExecutor;
    private List<Site> mSites;
    private View mOldView;
    private int mSearchToken;
    private int mSearchSuccessCount;
    private int mSearchFailureCount;
    private int mSearchTotalCount;

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, false);
    }

    public static void start(Activity activity, String keyword, boolean clear) {
        Intent intent = new Intent(activity, CollectActivity.class);
        if (clear) intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("keyword", keyword);
        activity.startActivityForResult(intent, 1000);
    }

    private CollectFragment getFragment() {
        return (CollectFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, 0);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        setRecyclerView();
        setViewModel();
        setPager();
        setSite();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.recycler.setSelectedPosition(position);
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(new CollectPresenter())));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void setSite() {
        mSites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (site.isSearchable()) mSites.add(site);
        Site home = VodConfig.get().getHome();
        if (!mSites.contains(home)) return;
        mSites.remove(home);
        mSites.add(0, home);
    }

    private void search() {
        mAdapter.add(Collect.all());
        mBinding.pager.getAdapter().notifyDataSetChanged();
        startSearchProgress();
        mExecutor = new PauseExecutor(Constant.THREAD_POOL * 2);
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        mBinding.progress.setVisibility(View.VISIBLE);
        int token = ++mSearchToken;
        for (Site site : mSites) mExecutor.execute(() -> search(site, token));
    }

    private void search(Site site, int token) {
        Result result = Result.empty();
        boolean success = false;
        try {
            result = mViewModel.searchResult(site, getKeyword(), false);
            success = true;
        } catch (Throwable ignored) {
        }
        Result finalResult = result;
        boolean finalSuccess = success;
        App.post(() -> onSearchFinished(token, finalResult, finalSuccess));
    }

    private void onSearchFinished(int token, Result result, boolean success) {
        if (token != mSearchToken) return;
        if (success) mSearchSuccessCount++;
        else mSearchFailureCount++;
        updateSearchProgress();
        if (!success || result.getList().isEmpty()) return;
        int exactMatchCount = countExactMatch(result.getList(), getKeyword());
        getFragment().addVideo(result.getList());
        addAllCollect(result.getList(), exactMatchCount);
        mAdapter.add(Collect.create(result.getList(), exactMatchCount));
        mBinding.pager.getAdapter().notifyDataSetChanged();
    }

    private void stop() {
        mSearchToken++;
        if (mExecutor == null) return;
        mExecutor.shutdownNow();
        mExecutor = null;
    }

    private void addAllCollect(List<com.github.tvbox.osc.bean.Vod> items, int exactMatchCount) {
        if (mAdapter.size() == 0) return;
        Collect all = (Collect) mAdapter.get(0);
        all.getList().addAll(items);
        all.setExactMatchCount(all.getExactMatchCount() + exactMatchCount);
        mAdapter.replace(0, all);
    }

    private void startSearchProgress() {
        mSearchSuccessCount = 0;
        mSearchFailureCount = 0;
        mSearchTotalCount = mSites.size();
        updateSearchProgress();
    }

    private void updateSearchProgress() {
        mBinding.progress.setText(getString(R.string.search_progress, mSearchSuccessCount, mSearchFailureCount, mSearchTotalCount));
    }

    private int countExactMatch(List<com.github.tvbox.osc.bean.Vod> items, String keyword) {
        int count = 0;
        String target = normalizeKeyword(keyword);
        if (target.isEmpty()) return 0;
        for (com.github.tvbox.osc.bean.Vod item : items) if (normalizeKeyword(item.getVodName()).equals(target)) count++;
        return count;
    }

    private String normalizeKeyword(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setActivated(false);
        if (child == null) return;
        mOldView = child.itemView;
        mOldView.setActivated(true);
        App.post(mRunnable, 200);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        setResult(RESULT_OK);
        finish();
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
    public void onBackPressed() {
        super.onBackPressed();
        stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return CollectFragment.newInstance(getKeyword(), (Collect) mAdapter.get(position));
        }

        @Override
        public int getCount() {
            return mAdapter.size();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }
    }
}
