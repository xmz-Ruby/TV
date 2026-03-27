package com.github.tvbox.osc.ui.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.tvbox.osc.App;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.api.config.VodConfig;
import com.github.tvbox.osc.bean.Class;
import com.github.tvbox.osc.bean.Config;
import com.github.tvbox.osc.bean.Hot;
import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.bean.Site;
import com.github.tvbox.osc.bean.Value;
import com.github.tvbox.osc.databinding.FragmentVodBinding;
import com.github.tvbox.osc.event.CastEvent;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.StateEvent;
import com.github.tvbox.osc.impl.Callback;
import com.github.tvbox.osc.impl.ConfigCallback;
import com.github.tvbox.osc.impl.FilterCallback;
import com.github.tvbox.osc.impl.SiteCallback;
import com.github.tvbox.osc.model.SiteViewModel;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.KeepActivity;
import com.github.tvbox.osc.ui.activity.VideoActivity;
import com.github.tvbox.osc.ui.adapter.TypeAdapter;
import com.github.tvbox.osc.ui.base.BaseFragment;
import com.github.tvbox.osc.ui.dialog.FilterDialog;
import com.github.tvbox.osc.ui.dialog.HistoryDialog;
import com.github.tvbox.osc.ui.dialog.LinkDialog;
import com.github.tvbox.osc.ui.dialog.ReceiveDialog;
import com.github.tvbox.osc.ui.dialog.SiteDialog;
import com.github.tvbox.osc.utils.FileChooser;
import com.github.tvbox.osc.utils.FileUtil;
import com.github.tvbox.osc.utils.Notify;
import com.github.tvbox.osc.utils.ResUtil;
import com.github.tvbox.osc.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.Response;

public class VodFragment extends BaseFragment implements SiteCallback, FilterCallback, TypeAdapter.OnClickListener, ConfigCallback {

    private FragmentVodBinding mBinding;
    private SiteViewModel mViewModel;
    private TypeAdapter mAdapter;
    private Runnable mRunnable;
    private List<String> mHots;
    private Result mResult;

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private TypeFragment getFragment() {
        return (TypeFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    private Site getSite() {
        return VodConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        setRecyclerView();
        setAppBarView();
        updateContentMode();
        updateConfigMode();
        setViewModel();
        showProgress();
        initHot();
        getHot();
    }

    @Override
    protected void initEvent() {
        mBinding.top.setOnClickListener(this::onTop);
        mBinding.link.setOnClickListener(this::onLink);
        mBinding.contentMode.setOnClickListener(this::onContentMode);
        mBinding.configMode.setOnClickListener(this::onConfigMode);
        mBinding.logo.setOnClickListener(this::onLogo);
        mBinding.logo.setOnLongClickListener(this::onRefresh);
        mBinding.hot.setOnClickListener(this::onHot);
        mBinding.site.setOnClickListener(this::onSite);
        mBinding.keep.setOnClickListener(this::onKeep);
        mBinding.retry.setOnClickListener(this::onRetry);
        mBinding.filter.setOnClickListener(this::onFilter);
        mBinding.search.setOnClickListener(this::onSearch);
        mBinding.searchIcon.setOnClickListener(this::onSearchIcon);
        mBinding.history.setOnClickListener(this::onHistory);
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.type.smoothScrollToPosition(position);
                mAdapter.setActivated(position);
                setFabVisible(position);
            }
        });
    }

    private void setSiteText() {
        String site = getSite().getName();
        mBinding.site.setText(site);
    }

    private void setAppBarView() {
        mBinding.searchInput.setVisibility(Setting.isHomeDisplayName() ? View.GONE : View.VISIBLE);
        mBinding.siteView.setVisibility(Setting.isHomeDisplayName() ? View.VISIBLE : View.GONE);
        mBinding.searchIcon.setVisibility(Setting.isHomeDisplayName() ? View.VISIBLE : View.GONE);
    }

    private void setRecyclerView() {
        mBinding.type.setHasFixedSize(true);
        mBinding.type.setItemAnimator(null);
        mBinding.type.setAdapter(mAdapter = new TypeAdapter(this));
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(getViewLifecycleOwner(), result -> setAdapter(mResult = result));
    }

    private void initHot() {
        mHots = Hot.get(Setting.getHot());
        App.post(mRunnable = this::updateHot, 0);
    }

    private void getHot() {
        OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1", Headers.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general")).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                mHots = Hot.get(response.body().string());
            }
        });
    }

    private void updateHot() {
        App.post(mRunnable, 10 * 1000);
        if (mHots.isEmpty() || mHots.size() < 10) return;
        mBinding.hot.setText(mHots.get(new SecureRandom().nextInt(11)));
    }

    private Result handle(Result result) {
        List<Class> types = new ArrayList<>();
        for (Class type : result.getTypes()) if (result.getFilters().containsKey(type.getTypeId())) type.setFilters(result.getFilters().get(type.getTypeId()));
        for (String cate : getSite().getCategories()) for (Class type : result.getTypes()) if (cate.equals(type.getTypeName())) types.add(type);
        result.setTypes(types);
        return result;
    }

    private void setAdapter(Result result) {
        mAdapter.addAll(handle(result));
        mBinding.pager.getAdapter().notifyDataSetChanged();
        setFabVisible(0);
        hideProgress();
        checkRetry();
    }

    private void setFabVisible(int position) {
        if (mAdapter.getItemCount() == 0) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.link.setVisibility(View.VISIBLE);
            mBinding.filter.setVisibility(View.GONE);
        } else if (mAdapter.get(position).getFilters().size() > 0) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.link.setVisibility(View.GONE);
            mBinding.filter.show();
        } else if (position == 0 || mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.filter.setVisibility(View.GONE);
            mBinding.link.show();
        }
    }

    private void checkRetry() {
        mBinding.retry.setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void onTop(View view) {
        getFragment().scrollToTop();
        mBinding.top.setVisibility(View.INVISIBLE);
        if (mBinding.filter.getVisibility() == View.INVISIBLE) mBinding.filter.show();
        else if (mBinding.link.getVisibility() == View.INVISIBLE) mBinding.link.show();
    }

    private void onLink(View view) {
        LinkDialog.create(this).show();
    }

    private void onContentMode(View view) {
        if (!VodConfig.get().hasShortDramaSites()) return;
        int mode = Setting.isVodContentShortDramaMode() ? Setting.VOD_CONTENT_MODE_FILM : Setting.VOD_CONTENT_MODE_SHORT_DRAMA;
        Setting.putVodContentMode(mode);
        updateContentMode();
        Config config = VodConfig.get().getConfig();
        if (!config.isEmpty()) setConfig(config, getString(R.string.vod_content_mode_switched, getCurrentContentModeText()));
    }

    private void onConfigMode(View view) {
        int mode = Setting.isConfigLoadMobileMode() ? Setting.CONFIG_LOAD_MODE_WIFI : Setting.CONFIG_LOAD_MODE_MOBILE;
        Setting.putConfigLoadMode(mode);
        updateConfigMode();
        Config config = VodConfig.get().getConfig();
        if (!config.isEmpty()) setConfig(config, getString(R.string.vod_config_mode_switched, getConfigModeText()));
    }

    private void onLogo(View view) {
        if (Setting.isHomeDisplayName()) HistoryDialog.create(this).type(0).show();
        else SiteDialog.create(this).change().show();
    }

    private void onSite(View view) {
        SiteDialog.create(this).change().show();
    }

    private boolean onRefresh(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                Config config = VodConfig.get().getConfig().json("").save();
                if (!config.isEmpty()) setConfig(config, ResUtil.getString(R.string.config_refreshed));
            }
        });
        return true;
    }

    private void onKeep(View view) {
        KeepActivity.start(getActivity());
    }

    private void onRetry(View view) {
        homeContent();
    }

    private void onFilter(View view) {
        if (mAdapter.getItemCount() > 0) FilterDialog.create().filter(mAdapter.get(mBinding.pager.getCurrentItem()).getFilters()).show(this);
    }

    private void onHot(View view) {
        CollectActivity.start(getActivity());
    }

    private void onSearch(View view) {
        CollectActivity.start(getActivity(), mBinding.hot.getText().toString());
    }

    private void onSearchIcon(View view) {
        CollectActivity.start(getActivity());
    }

    private void onHistory(View view) {
        HistoryActivity.start(getActivity());
    }

    private void showProgress() {
        mBinding.retry.setVisibility(View.GONE);
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void homeContent() {
        setSiteText();
        showProgress();
        setFabVisible(0);
        mAdapter.clear();
        mViewModel.homeContent();
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    private void setLogo() {
        Glide.with(this).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).listener(getListener()).into(mBinding.logo);
    }

    private RequestListener<Drawable> getListener() {
        return new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                mBinding.logo.getLayoutParams().width = ResUtil.dp2px(24);
                mBinding.logo.getLayoutParams().height = ResUtil.dp2px(24);
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                mBinding.logo.getLayoutParams().width = ResUtil.dp2px(32);
                mBinding.logo.getLayoutParams().height = ResUtil.dp2px(32);
                return false;
            }
        };
    }


    public Result getResult() {
        return mResult == null ? new Result() : mResult;
    }

    private void setConfig() {
        Notify.dismiss();
        RefreshEvent.video();
        RefreshEvent.config();
    }

    private void updateContentMode() {
        mBinding.contentMode.setVisibility(VodConfig.get().hasShortDramaSites() ? View.VISIBLE : View.GONE);
        if (VodConfig.get().hasShortDramaSites()) mBinding.contentMode.setText(getContentModeButtonText());
    }

    private String getContentModeButtonText() {
        return getString(Setting.isVodContentShortDramaMode() ? R.string.vod_content_mode_film_action : R.string.vod_content_mode_short_drama_action);
    }

    private String getCurrentContentModeText() {
        return getString(Setting.isVodContentShortDramaMode() ? R.string.vod_content_mode_short_drama : R.string.vod_content_mode_film);
    }

    private void updateConfigMode() {
        mBinding.configMode.setText(getConfigModeText());
    }

    private String getConfigModeText() {
        return getString(Setting.isConfigLoadMobileMode() ? R.string.vod_config_mode_mobile : R.string.vod_config_mode_wifi);
    }

    @Override
    public void setConfig(Config config) {
        setConfig(config, "");
    }

    private void setConfig(Config config, String success) {
        if (config.getUrl().startsWith("file") && !PermissionX.isGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> load(config, success));
        } else {
            load(config, success);
        }
    }

    private void load(Config config, String success) {
        switch (config.getType()) {
            case 0:
                Notify.progress(getActivity());
                VodConfig.load(config, getCallback(success));
                break;
        }
    }

    private Callback getCallback(String success) {
        return new Callback() {
            @Override
            public void success() {
                setConfig();
                if (!TextUtils.isEmpty(success)) Notify.show(success);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                setConfig();
            }
        };
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case VIDEO:
            case SIZE:
                homeContent();
                break;
            case CONFIG:
                setAppBarView();
                updateContentMode();
                updateConfigMode();
                setLogo();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.getType()) {
            case EMPTY:
                hideProgress();
                break;
            case PROGRESS:
                showProgress();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        ReceiveDialog.create().event(event).show(this);
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        setSiteText();
        homeContent();
    }

    @Override
    public void onChanged() {
    }

    @Override
    public void onItemClick(int position, Class item) {
        mBinding.pager.setCurrentItem(position);
        mAdapter.setActivated(position);
    }

    @Override
    public void setFilter(String key, Value value) {
        getFragment().setFilter(key, value);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || requestCode != FileChooser.REQUEST_PICK_FILE) return;
        VideoActivity.file(getActivity(), FileChooser.getPathFromUri(getContext(), data.getData()));
    }

    @Override
    public boolean canBack() {
        if (mBinding.pager.getAdapter() == null) return true;
        if (mBinding.pager.getAdapter().getCount() == 0) return true;
        return getFragment().canBack();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        App.removeCallbacks(mRunnable);
        EventBus.getDefault().unregister(this);
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return TypeFragment.newInstance(getSite().getKey(), type.getTypeId(), type.getStyle(), type.getExtend(true), "1".equals(type.getTypeFlag()));
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
