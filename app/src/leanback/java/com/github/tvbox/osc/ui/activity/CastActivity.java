package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import com.github.tvbox.osc.player.exo.CustomExoView;
import androidx.media3.ui.SubtitleView;
import androidx.viewbinding.ViewBinding;

import com.android.cast.dlna.dmr.CastAction;
import com.android.cast.dlna.dmr.DLNARendererService;
import com.android.cast.dlna.dmr.RenderControl;
import com.android.cast.dlna.dmr.RenderState;
import com.android.cast.dlna.dmr.RendererServiceBinder;
import com.android.cast.dlna.dmr.service.RendererInterfaceKt;
import com.github.tvbox.osc.App;
import com.github.tvbox.osc.Constant;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.bean.Sub;
import com.github.tvbox.osc.bean.Track;
import com.github.tvbox.osc.databinding.ActivityCastBinding;
import com.github.tvbox.osc.event.ActionEvent;
import com.github.tvbox.osc.event.ErrorEvent;
import com.github.tvbox.osc.event.PlayerEvent;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.IjkUtil;
import com.github.tvbox.osc.player.exo.ExoUtil;
import com.github.tvbox.osc.player.Players;
import com.github.tvbox.osc.ui.base.BaseActivity;
import com.github.tvbox.osc.ui.custom.CustomKeyDownCast;
import com.github.tvbox.osc.ui.dialog.PlayerDialog;
import com.github.tvbox.osc.ui.dialog.SubtitleDialog;
import com.github.tvbox.osc.ui.dialog.TrackDialog;
import com.github.tvbox.osc.utils.Clock;
import com.github.tvbox.osc.utils.KeyUtil;
import com.github.tvbox.osc.utils.ResUtil;
import com.github.tvbox.osc.utils.Traffic;

import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class CastActivity extends BaseActivity implements CustomKeyDownCast.Listener, TrackDialog.Listener, PlayerDialog.Listener, RenderControl, ServiceConnection, Clock.Callback {

    private ActivityCastBinding mBinding;
    private DLNARendererService mService;
    private CustomKeyDownCast mKeyDown;
    private RenderState mState;
    private CastAction mAction;
    private DIDLParser mParser;
    private Players mPlayers;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Clock mClock;
    private long position;
    private long duration;
    private int scale;
    private long lastMemoryCheck = 0;

    private CustomExoView getExo() {
        return mBinding.exo;
    }

    private IjkVideoView getIjk() {
        return mBinding.ijk;
    }

    private Drawable getDefaultArtwork() {
        if (mPlayers.isExo()) return getExo().getDefaultArtwork();
        return getIjk().getDefaultArtwork();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCastBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        checkAction();
    }

    @Override
    protected void initView() {
        bindService(new Intent(this, DLNARendererService.class), this, Context.BIND_AUTO_CREATE);
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownCast.create(this);
        mPlayers = Players.create(this);
        mParser = new DIDLParser();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::checkMemoryAndService;
        setVideoView();
        checkAction();
        startMemoryMonitor();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.control.seek.setListener(mPlayers);
        mBinding.control.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.speed.setDownListener(this::onSpeedSub);
        mBinding.control.text.setUpListener(this::onSubtitleClick);
        mBinding.control.text.setDownListener(this::onSubtitleClick);
        mBinding.control.text.setOnClickListener(this::onTrack);
        mBinding.control.audio.setOnClickListener(this::onTrack);
        mBinding.control.video.setOnClickListener(this::onTrack);
        mBinding.control.scale.setOnClickListener(view -> onScale());
        mBinding.control.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.reset.setOnClickListener(view -> onReset());
        mBinding.control.player.setOnClickListener(view -> onPlayer());
        mBinding.control.decode.setOnClickListener(view -> onDecode());
        mBinding.control.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
    }

    private String getName() {
        try {
            return mParser.parse(mAction.getCurrentURIMetaData()).getItems().get(0).getId();
        } catch (Exception e) {
            return mAction.getCurrentURI();
        }
    }

    private void checkAction() {
        mAction = getIntent().getParcelableExtra(RendererInterfaceKt.keyExtraCastAction);
        mBinding.widget.title.setText(getName());
        position = duration = 0;
        start();
    }

    private void start() {
        mPlayers.setMediaSource(mAction.getCurrentURI());
        showProgress();
        setMetadata();
        hideCenter();
    }

    private void setVideoView() {
        mPlayers.init(getExo(), getIjk());
        mPlayers.setPlayer(Setting.getPlayer());
        findViewById(R.id.timeBar).setNextFocusUpId(R.id.reset);
        mBinding.control.reset.setText(ResUtil.getStringArray(R.array.select_reset)[0]);
        setScale(scale = Setting.getScale());
        ExoUtil.setSubtitleView(mBinding.exo);
        IjkUtil.setSubtitleView(mBinding.ijk);
        setPlayerView();
        setDecodeView();
    }

    private void setPlayerView() {
        getIjk().setPlayer(mPlayers.getPlayer());
        mBinding.control.speed.setText(mPlayers.getSpeedText());
        mBinding.control.player.setText(mPlayers.getPlayerText());
        mBinding.control.speed.setEnabled(mPlayers.canAdjustSpeed());
        getExo().setVisibility(mPlayers.isExo() ? View.VISIBLE : View.GONE);
        getIjk().setVisibility(mPlayers.isIjk() ? View.VISIBLE : View.GONE);
        mBinding.control.decode.setVisibility(mPlayers.isExo() ? View.VISIBLE : View.GONE);
    }

    private void setDecodeView() {
        mBinding.control.decode.setText(mPlayers.getDecodeText());
    }

    private void setScale(int scale) {
        getExo().setScale(scale);
        getIjk().setResizeMode(scale);
        mBinding.control.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void onScale() {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        scale = scale == array.length - 1 ? 0 : ++scale;
        setScale(scale);
    }

    private void onSpeed() {
        mBinding.control.speed.setText(mPlayers.addSpeed());
    }

    private void onSpeedAdd() {
        mBinding.control.speed.setText(mPlayers.addSpeed(0.25f));
    }

    private void onSpeedSub() {
        mBinding.control.speed.setText(mPlayers.subSpeed(0.25f));
    }

    private boolean onSpeedLong() {
        mBinding.control.speed.setText(mPlayers.toggleSpeed());
        return true;
    }

    private void onReset() {
        start();
    }

    private void onPlayer() {
        PlayerDialog.create().select(mPlayers.getPlayer()).title(mBinding.widget.title.getText().toString()).show(this);
        hideControl();
    }

    private void onDecode() {
        onDecode(true);
    }

    private void onDecode(boolean save) {
        mPlayers.toggleDecode(save);
        mPlayers.init(getExo(), getIjk());
        mPlayers.setMediaSource();
        setDecodeView();
    }

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).vod(true).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    private void showProgress() {
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.widget.progress.setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showInfo() {
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.info.setVisibility(View.VISIBLE);
    }

    private void hideInfo() {
        mBinding.widget.center.setVisibility(View.GONE);
        mBinding.widget.info.setVisibility(View.GONE);
    }

    private void showControl() {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.control.reset.requestFocus();
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.text.setText(R.string.play_track_text);
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        hideInfo();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        App.post(mR2, Constant.INTERVAL_TRAFFIC);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void startMemoryMonitor() {
        App.post(mR3, 30000); // 每30秒检查一次
    }

    private void checkMemoryAndService() {
        try {
            // 检查内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            float memoryUsagePercent = (float) usedMemory / maxMemory * 100;

            if (memoryUsagePercent > 80) {
                android.util.Log.w("CastActivity", String.format(
                    "High memory usage: %.1f%% (%d MB / %d MB)",
                    memoryUsagePercent,
                    usedMemory / (1024 * 1024),
                    maxMemory / (1024 * 1024)
                ));

                // 建议垃圾回收
                if (memoryUsagePercent > 90) {
                    android.util.Log.w("CastActivity", "Critical memory usage, requesting GC");
                    System.gc();
                }
            }

            // 检查DLNA服务状态
            if (mService == null) {
                android.util.Log.w("CastActivity", "DLNA service is null, attempting to rebind");
                try {
                    bindService(new Intent(this, DLNARendererService.class), this, Context.BIND_AUTO_CREATE);
                } catch (Exception e) {
                    android.util.Log.e("CastActivity", "Failed to rebind DLNA service", e);
                }
            }

            // 检查投屏代理状态
            if (com.github.tvbox.osc.server.Server.get().isCasting()) {
                int activeConnections = com.github.tvbox.osc.server.Server.get().getCastProxyActiveConnections();
                long lastRequestTime = com.github.tvbox.osc.server.Server.get().getCastProxyLastRequestTime();
                long timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime;

                android.util.Log.d("CastActivity", String.format(
                    "Cast proxy status - Active connections: %d, Time since last request: %d ms",
                    activeConnections, timeSinceLastRequest
                ));

                // 如果超过60秒没有请求且没有活跃连接，可能播放已卡住
                if (timeSinceLastRequest > 60000 && activeConnections == 0 && lastRequestTime > 0) {
                    android.util.Log.w("CastActivity", "Cast proxy appears to be stuck, no requests for 60+ seconds");
                }
            }

        } catch (Exception e) {
            android.util.Log.e("CastActivity", "Error in memory/service check", e);
        }

        // 继续定期检查
        App.post(mR3, 30000);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (ActionEvent.PLAY.equals(event.getAction()) || ActionEvent.PAUSE.equals(event.getAction())) {
            onKeyCenter();
        } else if (ActionEvent.STOP.equals(event.getAction())) {
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.SUBTITLE) mPlayers.setSub(Sub.from(event.getPath()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerEvent(PlayerEvent event) {
        switch (event.getState()) {
            case 0:
                setTrackVisible(false);
                mClock.setCallback(this);
                setState(RenderState.PREPARING);
                break;
            case Player.STATE_IDLE:
                setState(RenderState.IDLE);
                break;
            case Player.STATE_BUFFERING:
                showProgress();
                setState(RenderState.PREPARING);
                break;
            case Player.STATE_READY:
                setMetadata();
                hideProgress();
                mPlayers.reset();
                setTrackVisible(true);
                // 立即更新duration和position，确保DLNA服务能返回正确的时长
                position = mPlayers.getPosition();
                duration = mPlayers.getDuration();
                android.util.Log.i("CastActivity", "Player ready - duration: " + duration + ", position: " + position);
                setState(RenderState.PLAYING);
                mBinding.widget.size.setText(mPlayers.getSizeText());
                break;
            case Player.STATE_ENDED:
                showControl();
                setState(RenderState.STOPPED);
                break;
        }
    }

    private void setTrackVisible(boolean visible) {
        mBinding.control.text.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_TEXT) ? View.VISIBLE : View.GONE);
        mBinding.control.audio.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.video.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    private void setMetadata() {
        mPlayers.setMetadata(mBinding.widget.title.getText().toString(), "", "", getDefaultArtwork());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        if (mPlayers.addRetry() > event.getRetry()) onError(event);
        else if (event.isDecode() && mPlayers.canToggleDecode()) onDecode(false);
        else if (event.isExo() && mPlayers.isExo()) onExoCheck(event);
        else onReset();
    }

    private void onExoCheck(ErrorEvent event) {
        if (event.getCode() == PlaybackException.ERROR_CODE_IO_UNSPECIFIED || event.getCode() >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED && event.getCode() <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) mPlayers.setFormat(ExoUtil.getMimeType(event.getCode()));
        mPlayers.setMediaSource();
    }

    private void onError(ErrorEvent event) {
        showError(event.getMsg());
        onStopped();
    }

    private void onPaused() {
        mBinding.widget.exoDuration.setText(mPlayers.getDurationTime());
        mBinding.widget.exoPosition.setText(mPlayers.getPositionTime(0));
        setState(RenderState.PAUSED);
        mPlayers.pause();
        showInfo();
    }

    private void onPlay() {
        setState(RenderState.PLAYING);
        mPlayers.play();
        hideCenter();
    }

    private void onStopped() {
        setState(RenderState.STOPPED);
        mPlayers.reset();
        mPlayers.stop();
    }

    private void setState(RenderState state) {
        if (mService != null) mService.notifyAvTransportLastChange(this.mState = state);
    }

    @NonNull
    @Override
    public RenderState getState() {
        return mState;
    }

    @Override
    public void onTrackClick(Track item) {
    }

    @Override
    public void onSubtitleClick() {
        App.post(this::hideControl, 200);
        SubtitleView subtitleView = mPlayers.isIjk() ? getIjk().getSubtitleView() : getExo().getSubtitleView();
        App.post(() -> SubtitleDialog.create().view(subtitleView).full(true).show(this), 200);
    }

    @Override
    public void onTimeChanged() {
        position = mPlayers.getPosition();
        duration = mPlayers.getDuration();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        try {
            mService = ((RendererServiceBinder) service).getService();
            if (mService != null) {
                mService.bindRealPlayer(this);
                android.util.Log.i("CastActivity", "DLNA service connected successfully");
            } else {
                android.util.Log.e("CastActivity", "DLNA service is null after connection");
            }
        } catch (Exception e) {
            android.util.Log.e("CastActivity", "Error connecting to DLNA service", e);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        android.util.Log.w("CastActivity", "DLNA service disconnected unexpectedly");
        mService = null;

        // 尝试重新绑定服务
        try {
            android.util.Log.i("CastActivity", "Attempting to rebind DLNA service");
            bindService(new Intent(this, DLNARendererService.class), this, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            android.util.Log.e("CastActivity", "Failed to rebind DLNA service", e);
        }
    }

    @Override
    public long getCurrentPosition() {
        return position;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public void seek(long time) {
        App.post(() -> mPlayers.seekTo(time));
    }

    @Override
    public void pause() {
        App.post(this::onPaused);
    }

    @Override
    public void play(@Nullable Double speed) {
        App.post(this::onPlay);
    }

    @Override
    public void stop() {
        App.post(this::finish);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) onToggle();
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event)) return mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onSeeking(int time) {
        mBinding.widget.exoDuration.setText(mPlayers.getDurationTime());
        mBinding.widget.exoPosition.setText(mPlayers.getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekTo(int time) {
        mKeyDown.resetTime();
        mPlayers.seekTo(time);
        showProgress();
        onPlay();
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isPlaying() || !mPlayers.canAdjustSpeed()) return;
        mBinding.control.speed.setText(mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        mBinding.control.speed.setText(mPlayers.setSpeed(1.0f));
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
    }

    @Override
    public void onKeyUp() {
        showControl();
    }

    @Override
    public void onKeyDown() {
        showControl();
    }

    @Override
    public void onKeyCenter() {
        if (mPlayers.isPlaying()) onPaused();
        else onPlay();
        hideControl();
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        onKeyCenter();
    }

    @Override
    public void onPlayerClick(Integer item) {
        mPlayers.setPlayer(item);
        setPlayerView();
        onReset();
    }

    @Override
    public void onPlayerShare(String title) {
        if (mPlayers.isEmpty()) return;
        mPlayers.choose(this, mBinding.widget.title.getText());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        onPlay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPlayers.pause();
        mClock.stop();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.util.Log.i("CastActivity", "onDestroy called");

        mClock.release();
        mPlayers.release();

        try {
            unbindService(this);
        } catch (Exception e) {
            android.util.Log.w("CastActivity", "Error unbinding service", e);
        }

        if (mService != null) {
            try {
                mService.bindRealPlayer(null);
            } catch (Exception e) {
                android.util.Log.w("CastActivity", "Error unbinding real player", e);
            }
        }

        // 移除所有回调，包括内存监控
        App.removeCallbacks(mR1, mR2, mR3);

        android.util.Log.i("CastActivity", "onDestroy completed");
    }
}
