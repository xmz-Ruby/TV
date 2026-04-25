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
import com.github.tvbox.osc.utils.CastDlna;
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
        android.util.Log.i("CastDialog", "Initializing DLNA Cast Manager...");
        DLNACastManager.INSTANCE.bindCastService(App.get());
        DLNACastManager.INSTANCE.registerDeviceListener(this);
        android.util.Log.i("CastDialog", "DLNA Cast Manager initialized, starting device search...");
    }

    private void onRefresh() {
        android.util.Log.i("CastDialog", "Refreshing device list...");
        adapter.clear();
        // HTTP设备扫描已禁用，只使用DLNA投屏
        // if (fm) {
        //     android.util.Log.d("CastDialog", "Starting FM device scan...");
        //     scanTask.start(adapter.getIps());
        // }
        DLNADevice.get().disconnect();
        android.util.Log.i("CastDialog", "Starting DLNA device search...");
        DLNACastManager.INSTANCE.search(null);
    }

    private void onCasted() {
        // 将投屏控制传递给VideoActivity，而不是跳转到独立页面
        FragmentActivity activity = getActivity();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            if (listener != null) {
                listener.onCastedWithControl(control);
                listener.onCasted();
            }
            dismiss();
        } else {
            android.util.Log.e("CastDialog", "Cannot cast: activity is null or finishing");
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
        android.util.Log.i("CastDialog", "DLNA device discovered: " + device.getDetails().getFriendlyName() + " (" + device.getIdentity().getUdn().getIdentifierString() + ")");
        adapter.addAll(DLNADevice.get().add(device));
    }

    @Override
    public void onDeviceRemoved(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        android.util.Log.i("CastDialog", "DLNA device removed: " + device.getDetails().getFriendlyName());
        adapter.remove(DLNADevice.get().remove(device));
    }

    @Override
    public void onConnected(@NonNull org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        android.util.Log.d("CastDialog", "onConnected - position: " + video.getPosition());

        // 保存当前连接的设备信息
        DLNADevice.get().setConnectedDevice(device);

        // 立即进入投屏控制页面
        onCasted();

        // 后台设置播放URL
        CastDlna.setAVTransportURI(control, video.getUrl(), video.getName(), video.getFormat(), this);
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

        // 设置代理 URL（用于 /media 接口显示实际投屏使用的 URL）
        com.github.tvbox.osc.server.Server.get().setCastProxyUrl(video.getUrl());
        android.util.Log.d("CastDialog", "Cast proxy URL: " + video.getUrl());

        // 保存视频总时长到Server（用于DLNA端没有返回duration时的回退方案）
        if (video.getDuration() > 0) {
            com.github.tvbox.osc.server.Server.get().updateCastProgress(video.getPosition(), video.getDuration());
            android.util.Log.d("CastDialog", "Saved video duration to Server: " + video.getDuration());
        }

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
        android.util.Log.d("CastDialog", "Device clicked - Name: " + item.getName() + ", Type: " + item.getType() + ", isDLNA: " + item.isDLNA());

        if (item.isDLNA()) {
            android.util.Log.i("CastDialog", "Connecting to DLNA device: " + item.getName());
            control = DLNACastManager.INSTANCE.connectDevice(DLNADevice.get().find(item), this);
        } else {
            android.util.Log.i("CastDialog", "Sending HTTP cast request to: " + item.getIp());
            OkHttp.newCall(client, item.getIp().concat("/action?do=cast"), body.build()).enqueue(this);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 注意：不在这里断开 DLNA 连接和清除投屏状态。
        // VideoActivity 的内嵌投屏控制仍然需要保持连接和状态。

        DLNACastManager.INSTANCE.unregisterListener(this);

        // 如果没有成功投屏（control 为 null），清除 token。
        // 已成功投屏时，后续由页面内的投屏控制流程负责清理状态。
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

        void onCastedWithControl(DeviceControl control);
    }
}
