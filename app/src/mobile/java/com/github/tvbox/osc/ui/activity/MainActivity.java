package com.github.tvbox.osc.ui.activity;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Updater;
import com.github.tvbox.osc.api.config.LiveConfig;
import com.github.tvbox.osc.api.config.PythonPreload;
import com.github.tvbox.osc.api.config.VodConfig;
import com.github.tvbox.osc.api.config.WallConfig;
import com.github.tvbox.osc.bean.Config;
import com.github.tvbox.osc.databinding.ActivityMainBinding;
import com.github.tvbox.osc.db.AppDatabase;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.event.StateEvent;
import com.github.tvbox.osc.impl.Callback;
import com.github.tvbox.osc.player.Source;
import com.github.tvbox.osc.receiver.ShortcutReceiver;
import com.github.tvbox.osc.server.Server;
import com.github.tvbox.osc.ui.base.BaseActivity;
import com.github.tvbox.osc.ui.custom.FragmentStateManager;
import com.github.tvbox.osc.ui.fragment.SettingCustomFragment;
import com.github.tvbox.osc.ui.fragment.SettingFragment;
import com.github.tvbox.osc.ui.fragment.SettingPlayerFragment;
import com.github.tvbox.osc.ui.fragment.VodFragment;
import com.github.tvbox.osc.utils.FileChooser;
import com.github.tvbox.osc.utils.Notify;
import com.github.tvbox.osc.utils.UrlUtil;
import com.google.android.material.navigation.NavigationBarView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends BaseActivity implements NavigationBarView.OnItemSelectedListener {

    private ActivityMainBinding mBinding;
    private FragmentStateManager mManager;
    private boolean confirm;
    private final Runnable hidePythonPreload = () -> {
        mBinding.pythonInitOverlay.setVisibility(View.GONE);
        PythonPreload.hide();
    };

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        initFragment(savedInstanceState);
        observePythonPreload();
        Server.get().start();
        initConfig();
    }

    @Override
    protected void initEvent() {
        mBinding.navigation.setOnItemSelectedListener(this);
        mBinding.navigation.findViewById(R.id.live).setOnLongClickListener(this::addShortcut);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
                loadLive("file:/" + FileChooser.getPathFromUri(this, intent.getData()));
            } else {
                VideoActivity.push(this, intent.getData().toString());
            }
        }
    }

    private void initFragment(Bundle savedInstanceState) {
        mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                if (position == 0) return VodFragment.newInstance();
                if (position == 1) return SettingFragment.newInstance();
                if (position == 2) return SettingPlayerFragment.newInstance();
                if (position == 3) return SettingCustomFragment.newInstance();
                return null;
            }
        };
        if (savedInstanceState == null) mManager.change(0);
    }

    private void initConfig() {
        WallConfig.get().init();
        LiveConfig.get().init().load();
        VodConfig.get().init().load(getCallback(), true);
    }

    private void observePythonPreload() {
        PythonPreload.observe().observe(this, this::renderPythonPreload);
    }

    private void renderPythonPreload(PythonPreload.State state) {
        if (state == null || !state.isVisible()) {
            App.removeCallbacks(hidePythonPreload);
            mBinding.pythonInitOverlay.setVisibility(View.GONE);
            return;
        }

        App.removeCallbacks(hidePythonPreload);
        mBinding.pythonInitOverlay.setVisibility(View.VISIBLE);
        ProgressBar progressBar = mBinding.pythonInitProgress;
        int progress = state.getTotal() > 0 ? Math.max(1, (int) ((state.getCompleted() * 100f) / state.getTotal())) : 0;
        progressBar.setProgress(progress);

        if (state.isCompletedAll()) {
            mBinding.pythonInitTitle.setText(R.string.python_preload_done_title);
            mBinding.pythonInitStatus.setText(getString(R.string.python_preload_done_status, state.getSuccess(), state.getFail()));
            long elapsed = Math.max(0, System.currentTimeMillis() - state.getStartedAt());
            long delay = Math.max(2500, 4000 - elapsed);
            App.post(hidePythonPreload, delay);
            return;
        }

        mBinding.pythonInitTitle.setText(R.string.python_preload_title);
        if (android.text.TextUtils.isEmpty(state.getSiteName())) {
            mBinding.pythonInitStatus.setText(getString(R.string.python_preload_status, state.getCompleted(), state.getTotal(), state.getSuccess(), state.getFail()));
        } else {
            mBinding.pythonInitStatus.setText(getString(R.string.python_preload_status_site, state.getCompleted(), state.getTotal(), state.getSuccess(), state.getFail(), state.getSiteName()));
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success(String result) {
                Notify.show(result);
            }

            @Override
            public void success() {
                checkAction(getIntent());
                RefreshEvent.config();
                RefreshEvent.video();
            }

            @Override
            public void error(String msg) {
                RefreshEvent.config();
                StateEvent.empty();
                Notify.show(msg);
            }
        };
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                openLive();
            }
        });
    }

    private void setNavigation() {
        mBinding.navigation.getMenu().findItem(R.id.vod).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.setting).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.live).setVisible(LiveConfig.hasUrl());
    }

    private boolean openLive() {
        LiveActivity.start(this);
        return false;
    }

    private boolean addShortcut(View view) {
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, getString(R.string.nav_live)).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher)).setIntent(new Intent(Intent.ACTION_VIEW, null, this, LiveActivity.class)).setShortLabel(getString(R.string.nav_live)).build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, ShortcutReceiver.class).setAction(ShortcutReceiver.ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ShortcutManagerCompat.requestPinShortcut(this, info, pendingIntent.getIntentSender());
        return true;
    }

    private void setConfirm() {
        confirm = true;
        Notify.show(R.string.app_exit);
        App.post(() -> confirm = false, 5000);
    }

    public void change(int position) {
        mManager.change(position);
    }

    @Override
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
        if (event.getType().equals(RefreshEvent.Type.CONFIG)) setNavigation();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.getType() != ServerEvent.Type.PUSH) return;
        VideoActivity.push(this, event.getText());
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (mBinding.navigation.getSelectedItemId() == item.getItemId()) return false;
        if (item.getItemId() == R.id.vod) return mManager.change(0);
        if (item.getItemId() == R.id.setting) return mManager.change(1);
        if (item.getItemId() == R.id.live) return openLive();
        return false;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        RefreshEvent.video();
    }

    protected boolean handleBack() {
        return true;
    }

    @Override
    protected void onBackPress() {
        if (!mBinding.navigation.getMenu().findItem(R.id.vod).isVisible()) {
            setNavigation();
        } else if (mManager.isVisible(3)) {
            change(1);
        } else if (mManager.isVisible(2)) {
            change(1);
        } else if (mManager.isVisible(1)) {
            mBinding.navigation.setSelectedItemId(R.id.vod);
        } else if (mManager.canBack(0)) {
            if (!confirm) setConfirm();
            else finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WallConfig.get().clear();
        LiveConfig.get().clear();
        VodConfig.get().clear();
        Source.get().exit();
        Server.get().stop();
    }
}
