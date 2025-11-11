package com.github.tvbox.osc.ui.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.Updater;
import com.github.tvbox.osc.api.config.LiveConfig;
import com.github.tvbox.osc.api.config.VodConfig;
import com.github.tvbox.osc.api.config.WallConfig;
import com.github.tvbox.osc.bean.Config;
import com.github.tvbox.osc.bean.Live;
import com.github.tvbox.osc.bean.Site;
import com.github.tvbox.osc.databinding.FragmentSettingBinding;
import com.github.tvbox.osc.db.AppDatabase;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.impl.BackupCallback;
import com.github.tvbox.osc.impl.Callback;
import com.github.tvbox.osc.impl.ConfigCallback;
import com.github.tvbox.osc.impl.LiveCallback;
import com.github.tvbox.osc.impl.ProxyCallback;
import com.github.tvbox.osc.impl.SiteCallback;
import com.github.tvbox.osc.player.Source;
import com.github.tvbox.osc.ui.activity.MainActivity;
import com.github.tvbox.osc.ui.base.BaseFragment;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.ConfigDialog;
import com.github.tvbox.osc.ui.dialog.DanmuServerDialog;
import com.github.tvbox.osc.ui.dialog.DanmuServerHistoryDialog;
import com.github.tvbox.osc.ui.dialog.HistoryDialog;
import com.github.tvbox.osc.ui.dialog.LiveDialog;
import com.github.tvbox.osc.ui.dialog.ProxyDialog;
import com.github.tvbox.osc.ui.dialog.SiteDialog;
import com.github.tvbox.osc.ui.dialog.TransmitActionDialog;
import com.github.tvbox.osc.ui.dialog.TransmitDialog;
import com.github.tvbox.osc.utils.FileChooser;
import com.github.tvbox.osc.utils.FileUtil;
import com.github.tvbox.osc.utils.Notify;
import com.github.tvbox.osc.utils.ResUtil;
import com.github.tvbox.osc.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SettingFragment extends BaseFragment implements BackupCallback, ConfigCallback, SiteCallback, LiveCallback, ProxyCallback {

    private FragmentSettingBinding mBinding;
    private String[] backup;
    private int type;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private MainActivity getRoot() {
        return (MainActivity) getActivity();
    }

    private String getDanmuServerDesc() {
        String host = Setting.getDanmuHost();
        return TextUtils.isEmpty(host) ? getString(R.string.setting_on) : host;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.danmuServerUrl.setText(getDanmuServerDesc());
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        mBinding.backupText.setText((backup = ResUtil.getStringArray(R.array.select_backup))[Setting.getBackupMode()]);
        mBinding.aboutText.setText(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_api + "-" + BuildConfig.FLAVOR_abi);
        mBinding.proxyText.setText(UrlUtil.scheme(Setting.getProxy()));
        setCacheText();
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.danmuServer.setOnClickListener(this::onDanmuServer);
        mBinding.danmuServerHistory.setOnClickListener(this::onDanmuServerHistory);
        mBinding.proxy.setOnClickListener(this::onProxy);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.cache.setOnLongClickListener(this::onCacheLongClick);
        mBinding.transmit.setOnClickListener(this::onTransmit);
        mBinding.pull.setOnClickListener(this::onPull);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.backup.setOnLongClickListener(this::onBackupMode);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.version.setOnLongClickListener(this::onVersionDev);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.custom.setOnClickListener(this::onCustom);
        mBinding.about.setOnClickListener(this::onAbout);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file") && !PermissionX.isGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                Notify.progress(getActivity());
                VodConfig.load(config, getCallback());
                mBinding.vodUrl.setText(config.getDesc());
                break;
            case 1:
                Notify.progress(getActivity());
                LiveConfig.load(config, getCallback());
                mBinding.liveUrl.setText(config.getDesc());
                break;
            case 2:
                Notify.progress(getActivity());
                WallConfig.load(config, getCallback());
                mBinding.wallUrl.setText(config.getDesc());
                break;
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
                setConfig();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                setConfig();
            }
        };
    }

    private void setConfig() {
        switch (type) {
            case 0:
                Notify.dismiss();
                RefreshEvent.video();
                RefreshEvent.config();
                break;
            case 1:
                Notify.dismiss();
                RefreshEvent.config();
                break;
            case 2:
                Notify.dismiss();
                RefreshEvent.config();
                break;
        }
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        RefreshEvent.video();
    }

    @Override
    public void onChanged() {
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create(this).type(type = 0).show();
    }

    private void onLive(View view) {
        ConfigDialog.create(this).type(type = 1).show();
    }

    private void onWall(View view) {
        ConfigDialog.create(this).type(type = 2).show();
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create(this).type(type = 0).edit().show();
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create(this).type(type = 1).edit().show();
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create(this).type(type = 2).edit().show();
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create(this).all().show();
    }

    private void onLiveHome(View view) {
        LiveDialog.create(this).action().show();
    }

    private void onVodHistory(View view) {
        HistoryDialog.create(this).type(type = 0).show();
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create(this).type(type = 1).show();
    }

    private void onDanmuServer(View view) {
        DanmuServerDialog.create(this, url -> mBinding.danmuServerUrl.setText(getDanmuServerDesc())).show();
    }

    private void onDanmuServerHistory(View view) {
        DanmuServerHistoryDialog.create(this, url -> mBinding.danmuServerUrl.setText(getDanmuServerDesc())).show();
    }

    private void onPlayer(View view) {
        getRoot().change(2);
    }

    private void onCustom(View view) {
        getRoot().change(3);
    }

    private void onAbout(View view) {
        mBinding.aboutText.setText(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_api + "-" + BuildConfig.FLAVOR_abi);
    }

    private void onVersion(View view) {
        Updater.get().force().release().start(getActivity());
    }

    private boolean onVersionDev(View view) {
        Updater.get().force().dev().start(getActivity());
        return true;
    }

    private void setWallDefault(View view) {
        WallConfig.refresh(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
    }

    private void setWallRefresh(View view) {
        Notify.progress(getActivity());
        WallConfig.get().load(new Callback() {
            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }
        });
    }

    private void setDoh(View view) {
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.setting_doh).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
            setDoh(VodConfig.get().getDoh().get(which));
            dialog.dismiss();
        }).show();
    }

    private void setDoh(Doh doh) {
        Source.get().stop();
        OkHttp.get().setDoh(doh);
        Notify.progress(getActivity());
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
        VodConfig.load(Config.vod(), getCallback());
    }

    private void onProxy(View view) {
        ProxyDialog.create(this).show();
    }

    @Override
    public void setProxy(String proxy) {
        Source.get().stop();
        Setting.putProxy(proxy);
        OkHttp.selector().clear();
        OkHttp.get().setProxy(proxy);
        Notify.progress(getActivity());
        VodConfig.load(Config.vod(), getCallback());
        mBinding.proxyText.setText(UrlUtil.scheme(proxy));
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                VodConfig.get().getConfig().json("").save();
                setCacheText();
            }
        });
    }

    private boolean onCacheLongClick(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
                Config config = VodConfig.get().getConfig().json("").save();
                if (!config.isEmpty()) setConfig(config);
            }
        });
        return true;
    }

    private void onRestore(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
            if (allGranted) BackupDialog.create(this).show();
        });
    }

    private void onTransmit(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> {
            if (allGranted) TransmitActionDialog.create(this).show();
        });
    }

    private void onPull(View view) {
        new MaterialAlertDialogBuilder(getActivity()).setTitle(R.string.transmit_pull_restore).setMessage(R.string.transmit_pull_restore_desc).setNegativeButton(R.string.dialog_negative, null).setCancelable(true).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
            TransmitDialog.create().pullRetore().show(this);
            dialog.dismiss();
        }).show();
    }

    private void initConfig() {
        WallConfig.get().init();
        LiveConfig.get().init().load();
        VodConfig.get().init().load(getCallback());
    }

    @Override
    public void restore(File file) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> AppDatabase.restore(file, new Callback() {
            @Override
            public void success() {
                if (allGranted) {
                    Notify.progress(getActivity());
                    App.post(() -> {
                        AppDatabase.reset();
                        initConfig();
                    }, 3000);
                }
            }
        }));
    }

    private void onBackup(View view) {
        PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> AppDatabase.backup(new Callback() {
            @Override
            public void success(String path) {
                Notify.show(R.string.backed);
            }
        }));
    }

    private boolean onBackupMode(View view) {
        int index = Setting.getBackupMode();
        Setting.putBackupMode(index = index == backup.length - 1 ? 0 : ++index);
        mBinding.backupText.setText(backup[index]);
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.danmuServerUrl.setText(getDanmuServerDesc());
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        setCacheText();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || requestCode != FileChooser.REQUEST_PICK_FILE) return;
        String path = FileChooser.getPathFromUri(getContext(), data.getData());
        if (FileChooser.type() == FileChooser.TYPE_APK) TransmitDialog.create().apk(path).show(getActivity());
        else if (FileChooser.type() == FileChooser.TYPE_PUSH_WALLPAPER) TransmitDialog.create().wallConfig(path).show(getActivity());
        else setConfig(Config.find("file:/" + path.replace(Path.rootPath(), ""), type));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case CONFIG:
                setCacheText();
                mBinding.vodUrl.setText(VodConfig.getDesc());
                mBinding.liveUrl.setText(LiveConfig.getDesc());
                mBinding.wallUrl.setText(WallConfig.getDesc());
                mBinding.danmuServerUrl.setText(getDanmuServerDesc());
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }

}
