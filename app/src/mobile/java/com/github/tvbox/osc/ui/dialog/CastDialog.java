package com.github.tvbox.osc.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.android.cast.dlna.dmc.OnDeviceRegistryListener;
import com.android.cast.dlna.dmc.control.DeviceControl;
import com.android.cast.dlna.dmc.control.OnDeviceControlListener;
import com.android.cast.dlna.dmc.control.ServiceActionCallback;
import com.github.tvbox.osc.App;
import com.github.tvbox.osc.Constant;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.bean.Config;
import com.github.tvbox.osc.bean.Device;
import com.github.tvbox.osc.bean.History;
import com.github.tvbox.osc.databinding.DialogDeviceBinding;
import com.github.tvbox.osc.server.Server;
import com.github.tvbox.osc.ui.adapter.DeviceAdapter;
import com.github.tvbox.osc.utils.DLNADevice;
import com.github.tvbox.osc.utils.Notify;
import com.github.tvbox.osc.utils.ScanTask;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.fourthline.cling.support.lastchange.EventedValue;
import org.fourthline.cling.support.model.TransportState;

import java.io.IOException;
import java.util.List;

import kotlin.Unit;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class CastDialog extends BaseDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener, OnDeviceRegistryListener, OnDeviceControlListener, ServiceActionCallback<Unit>, okhttp3.Callback {

    private final FormBody.Builder body;
    private final OkHttpClient client;
    private final ScanTask scanTask;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private DeviceControl control;
    private Listener listener;
    private CastVideo video;
    private boolean fm;
    private boolean seekPending;
    private boolean hasSeeked;

    public static CastDialog create() {
        return new CastDialog();
    }

    public CastDialog() {
        scanTask = new ScanTask(this);
        body = new FormBody.Builder();
        body.add("device", Device.get().toString());
        body.add("config", Config.vod().toString());
        client = OkHttp.client(Constant.TIMEOUT_SYNC);

        // 在创建投屏对话框时就生成 token，确保构建 CastVideo URL 时 token 已存在
        Server.get().generateAndSetCastProxyToken();
    }

    public CastDialog history(History history) {
        String id = history.getVodId();
        String fd = history.getVodId();
        if (fd.startsWith("/")) fd = Server.get().getAddress() + "/file" + fd.replace(Path.rootPath(), "");
        if (fd.startsWith("file")) fd = Server.get().getAddress() + "/" + fd.replace(Path.rootPath(), "").replace("://", "");
        if (fd.contains("127.0.0.1")) fd = fd.replace("127.0.0.1", Util.getIp());
        body.add("history", history.toString().replace(id, fd));
        return this;
    }

    public CastDialog video(CastVideo video) {
        this.video = video;
        return this;
    }

    public CastDialog fm(boolean fm) {
        this.fm = fm;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
        this.listener = (Listener) activity;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setRecyclerView();
        getDevice();
        initDLNA();
    }

    @Override
    protected void initEvent() {
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
    }

    private void getDevice() {
        if (fm) adapter.addAll(Device.getAll());
        adapter.addAll(DLNADevice.get().getAll());
    }

    private void initDLNA() {
        DLNACastManager.INSTANCE.bindCastService(App.get());
        DLNACastManager.INSTANCE.registerDeviceListener(this);
    }

    private void onRefresh() {
        adapter.clear();
        if (fm) scanTask.start(adapter.getIps());
        DLNADevice.get().disconnect();
        DLNACastManager.INSTANCE.search(null);
    }

    private void onCasted() {
        // 启动投屏控制页面
        // 使用 getActivity() 而不是 getContext()，因为 getContext() 可能在异步回调时返回 null
        FragmentActivity activity = getActivity();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            com.github.tvbox.osc.ui.activity.CastControlActivity.start(activity, control);
            if (listener != null) {
                listener.onCasted();
            }
            dismiss();
        } else {
            android.util.Log.e("CastDialog", "Cannot start CastControlActivity: activity is null or finishing");
            // 清理投屏状态
            Server.get().setCasting(false, null);
        }
    }

    @Override
    public void onFind(List<Device> devices) {
        if (devices.size() > 0) adapter.addAll(devices);
    }

    @Override
    public void onDeviceAdded(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        adapter.addAll(DLNADevice.get().add(device));
    }

    @Override
    public void onDeviceRemoved(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        adapter.remove(DLNADevice.get().remove(device));
    }

    @Override
    public void onConnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        android.util.Log.d("CastDialog", "onConnected - position: " + video.getPosition());

        // 立即进入投屏控制页面
        onCasted();

        // 后台设置播放URL
        control.setAVTransportURI(video.getUrl(), video.getName(), this);
    }

    @Override
    public void onDisconnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        Notify.show(R.string.device_offline);
    }

    @Override
    public void onSuccess(Unit unit) {
        android.util.Log.d("CastDialog", "onSuccess - seeking to position: " + video.getPosition());

        // 设置投屏状态（用于 Emby 回传）
        // 使用 originalUrl 而不是代理后的 URL，以便正确恢复播放进度
        String urlForTracking = video.getOriginalUrl() != null ? video.getOriginalUrl() : video.getUrl();
        com.github.tvbox.osc.server.Server.get().setCasting(true, urlForTracking);
        android.util.Log.d("CastDialog", "Cast URL for tracking: " + urlForTracking);

        seekPending = video.getPosition() > 0;
        hasSeeked = false;
        control.play("1", null);
        if (seekPending) {
            // 延迟 5 秒执行 seek，给播放器足够的时间准备
            App.post(() -> performSeek(), 5000);
        }
        // 不再在这里调用 onCasted()，因为已经在 onConnected 时调用了
    }

    private void performSeek() {
        if (!seekPending || hasSeeked) return;
        hasSeeked = true;
        android.util.Log.d("CastDialog", "Performing delayed seek to: " + video.getPosition());
        control.seek(video.getPosition(), new ServiceActionCallback<Unit>() {
            @Override
            public void onSuccess(Unit result) {
                android.util.Log.d("CastDialog", "Seek successful!");
                seekPending = false;
            }

            @Override
            public void onFailure(@NonNull String error) {
                android.util.Log.e("CastDialog", "Seek failed: " + error);
                seekPending = false;
            }
        });
    }

    @Override
    public void onFailure(@NonNull String s) {
        Notify.show(s);
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        App.post(() -> Notify.show(e.getMessage()));
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        if (response.body().string().equals("OK")) App.post(this::onCasted);
        else App.post(() -> Notify.show(R.string.device_offline));
    }

    @Override
    public void onItemClick(Device item) {
        if (item.isDLNA()) control = DLNACastManager.INSTANCE.connectDevice(DLNADevice.get().find(item), this);
        else OkHttp.newCall(client, item.getIp().concat("/action?do=cast"), body.build()).enqueue(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 注意：不在这里断开 DLNA 连接和清除投屏状态
        // 因为投屏控制页面还在运行，需要保持连接
        // 连接和状态将在 CastControlActivity 中管理

        // 只取消注册监听器
        DLNACastManager.INSTANCE.unregisterListener(this);
        // 不要 unbind service，因为 CastControlActivity 还需要使用

        // 如果没有成功投屏（control 为 null），清除 token
        // 如果已经成功投屏，token 将在 CastControlActivity 销毁时清除
        if (control == null) {
            Server.get().setCasting(false, null);
            android.util.Log.d("CastDialog", "Cast dialog closed without casting, token cleared");
        }
    }

    @Override
    public void onAvTransportStateChanged(@NonNull TransportState state) {
    }

    @Override
    public void onEventChanged(@NonNull EventedValue<?> event) {
    }

    @Override
    public void onRendererVolumeChanged(int volume) {
    }

    @Override
    public void onRendererVolumeMuteChanged(boolean mute) {
    }

    @Override
    public boolean onLongClick(Device item) {
        return false;
    }

    public interface Listener {

        void onCasted();
    }
}
