package com.github.tvbox.osc.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.Constant;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.bean.Channel;
import com.github.tvbox.osc.bean.Drm;
import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.bean.Sub;
import com.github.tvbox.osc.bean.Track;
import com.github.tvbox.osc.event.ActionEvent;
import com.github.tvbox.osc.event.ErrorEvent;
import com.github.tvbox.osc.event.PlayerEvent;
import com.github.tvbox.osc.impl.ParseCallback;
import com.github.tvbox.osc.impl.SessionCallback;
import com.github.tvbox.osc.player.exo.ExoUtil;
import com.github.tvbox.osc.server.Server;
import com.github.tvbox.osc.utils.FileUtil;
import com.github.tvbox.osc.utils.Notify;
import com.github.tvbox.osc.utils.ResUtil;
import com.github.tvbox.osc.utils.UrlUtil;
import com.github.tvbox.osc.utils.Util;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.ui.widget.DanmakuView;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class Players implements Player.Listener, IMediaPlayer.Listener, ParseCallback, DrawHandler.Callback {

    private static final String TAG = Players.class.getSimpleName();
    private static final long BUFFER_STALL_CHECK_INTERVAL = 3_000L;
    private static final long STARTUP_BUFFERED_AHEAD_THRESHOLD_MS = 1_500L;
    private static final long BUFFER_PROGRESS_RESET_THRESHOLD_MS = 1_000L;
    private static final long BUFFER_PROGRESS_RESET_THRESHOLD_BYTES = 512 * 1024L;
    private static final long BUFFER_STALL_STARTUP_TIMEOUT = 15_000L;
    private static final long BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT = 12_000L;
    private static final long BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT = 12_000L;
    private static final long BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT = 18_000L;
    private static final long BUFFER_STALL_PLAYBACK_TIMEOUT = 25_000L;
    private static final long MOBILE_BUFFER_STALL_STARTUP_TIMEOUT = 20_000L;
    private static final long MOBILE_BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT = 18_000L;
    private static final long MOBILE_BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT = 16_000L;
    private static final long MOBILE_BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT = 22_000L;
    private static final long MOBILE_BUFFER_STALL_PLAYBACK_TIMEOUT = 30_000L;
    private static final long LEANBACK_BUFFER_STALL_STARTUP_TIMEOUT = 24_000L;
    private static final long LEANBACK_BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT = 21_000L;
    private static final long LEANBACK_BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT = 20_000L;
    private static final long LEANBACK_BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT = 28_000L;
    private static final long LEANBACK_BUFFER_STALL_PLAYBACK_TIMEOUT = 36_000L;
    private static final long LOW_PERFORMANCE_TV_BUFFER_STALL_STARTUP_TIMEOUT = 22_000L;
    private static final long LOW_PERFORMANCE_TV_BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT = 18_000L;
    private static final long LOW_PERFORMANCE_TV_BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT = 18_000L;
    private static final long LOW_PERFORMANCE_TV_BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT = 24_000L;
    private static final long LOW_PERFORMANCE_TV_BUFFER_STALL_PLAYBACK_TIMEOUT = 32_000L;
    private static final long VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_STARTUP_TIMEOUT = 30_000L;
    private static final long VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT = 26_000L;
    private static final long VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT = 24_000L;
    private static final long VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT = 34_000L;
    private static final long VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_PLAYBACK_TIMEOUT = 42_000L;
    private static final long EXO_SEEK_FRAME_TIMEOUT_MS = 2_500L;
    private static final long EXO_SEEK_FRAME_RECHECK_MS = 1_500L;
    private static final int EXO_SEEK_SURFACE_RECOVERY_LIMIT = 1;

    public static final int SYS = 0;
    public static final int IJK = 1;
    public static final int EXO = 2;

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private final StringBuilder builder;
    private final Formatter formatter;
    private final Runnable runnable;
    private final Runnable stallRunnable;
    private final Runnable seekFrameRunnable;
    private final PlaybackLockManager playbackLockManager;

    private Map<String, String> headers;
    private MediaSessionCompat session;
    private IjkVideoView ijkPlayer;
    private IjkVideoView ijkView;
    private DanmakuView danmuView;
    private VideoPlayerSync danmuSync;
    private ExoPlayer exoPlayer;
    private PlayerView exoView;
    private HandlerThread playbackThread;
    private ParseJob parseJob;
    private List<Sub> subs;
    private String format;
    private String url;
    private Drm drm;
    private Sub sub;

    private long position;
    private long prepareStartedAt;
    private long bufferingStartedAt;
    private long bufferingStartPosition;
    private long bufferingStartBufferedPosition;
    private long bufferingStartCachedBytes;
    private int decode;
    private int count;
    private int player;
    private int retry;
    private int exoSeekSurfaceRecoveryCount;
    private boolean buffering;
    private boolean exoAwaitingSeekFrame;
    private long exoPendingSeekPosition;
    private long exoSeekGeneration;

    public static Players create(Activity activity) {
        Players player = new Players(activity);
        Server.get().setPlayer(player);
        return player;
    }

    public static boolean isExo(int type) {
        return type == EXO;
    }

    public static boolean isHard(int decode) {
        return decode == HARD;
    }

    public boolean isHard() {
        return decode == HARD;
    }

    public boolean isSoft() {
        return decode == SOFT;
    }

    public boolean isExo() {
        return player == EXO;
    }

    public boolean isIjk() {
        return player == SYS || player == IJK;
    }

    private Players(Activity activity) {
        player = Setting.getPlayer();
        decode = Setting.getDecode(player);
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        stallRunnable = this::checkPlaybackStall;
        seekFrameRunnable = this::checkExoSeekFrame;
        playbackLockManager = new PlaybackLockManager(activity);
        formatter = new Formatter(builder, Locale.getDefault());
        position = C.TIME_UNSET;
        exoPendingSeekPosition = C.TIME_UNSET;
        bufferingStartPosition = C.TIME_UNSET;
        bufferingStartBufferedPosition = C.TIME_UNSET;
        createSession(activity);
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 0, new Intent(App.get(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void init(PlayerView exo, IjkVideoView ijk) {
        releaseExo();
        releaseIjk();
        exoView = exo;
        ijkView = ijk;
        initExo(exoView);
        initIjk(ijkView);
    }

    private void initExo(PlayerView view) {
        quitPlaybackThread();
        playbackThread = new HandlerThread("ExoPlayback", Process.THREAD_PRIORITY_URGENT_DISPLAY);
        playbackThread.start();
        ExoPlayer.Builder builder = new ExoPlayer.Builder(App.get())
                .setLoadControl(ExoUtil.buildLoadControl())
                .setTrackSelector(ExoUtil.buildTrackSelector())
                .setRenderersFactory(ExoUtil.buildRenderersFactory(decode))
                .setMediaSourceFactory(ExoUtil.buildMediaSourceFactory())
                .setPlaybackLooper(playbackThread.getLooper());
        if (ExoUtil.isLowPerformanceTv()) {
            builder.setStuckPlayingDetectionTimeoutMs(30_000);
        }
        exoPlayer = builder.build();
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, !Setting.isPlayWithOthers());
        if (!ExoUtil.isLowPerformanceTv()) {
            exoPlayer.addAnalyticsListener(new EventLogger());
            exoPlayer.addAnalyticsListener(new DroppedFrameListener());
        }
        exoPlayer.setHandleAudioBecomingNoisy(true);
        // Note: Media3 1.9.2 no longer supports dynamic surface type switching at runtime
        // Surface type is now set via XML attribute or during view creation
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
    }

    private void initIjk(IjkVideoView view) {
        ijkPlayer = view.render(Setting.getRender()).decode(decode);
        ijkPlayer.addListener(this);
        ijkPlayer.setPlayer(player);
    }

    public void setDanmuView(DanmakuView view) {
        view.setCallback(this);
        danmuView = view;
        // 设置视频播放器同步器，确保弹幕时间与视频时间保持同步
        if (danmuView.getConfig() != null) {
            danmuSync = new VideoPlayerSync(this);
            danmuView.getConfig().setDanmakuSync(danmuSync);
        }
    }

    public ExoPlayer exo() {
        return exoPlayer;
    }

    public IjkVideoView ijk() {
        return ijkPlayer;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setSub(Sub sub) {
        this.sub = sub;
        if (isIjk()) return;
        setPosition(getPosition());
        setMediaSource();
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    public void setMetadata(MediaMetadataCompat metadata) {
        session.setMetadata(metadata);
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        if (this.player != player) reset();
        if (this.player != player) stop();
        this.player = player;
        this.decode = Setting.getDecode(player);
    }

    public int getDecode(int player) {
        return Setting.getDecode(player);
    }

    public int getDecode() {
        return decode;
    }

    public void setDecode(int player, int decode) {
        Setting.putDecode(player, decode);
    }

    public void applyDecode(int decode, boolean save) {
        this.decode = decode;
        if (save) setDecode(player, decode);
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public boolean canToggleDecode() {
        return isExo() && ++count <= 1;
    }

    public void reset() {
        position = C.TIME_UNSET;
        prepareStartedAt = 0;
        removeTimeoutCheck();
        clearBufferingWatchdog();
        stopParse();
        count = 0;
        retry = 0;
    }

    public void clear() {
        headers = null;
        format = null;
        subs = null;
        drm = null;
        url = null;
    }

    public int addRetry() {
        return ++retry;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public int getVideoWidth() {
        return isExo() ? exoPlayer.getVideoSize().width : ijkPlayer.getVideoWidth();
    }

    public int getVideoHeight() {
        return isExo() ? exoPlayer.getVideoSize().height : ijkPlayer.getVideoHeight();
    }

    public float getSpeed() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackParameters().speed;
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getSpeed();
        return 1.0f;
    }

    public long getPosition() {
        if (isExo() && exoPlayer != null) return exoPlayer.getCurrentPosition();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getCurrentPosition();
        return 0;
    }

    public long getDuration() {
        if (isExo() && exoPlayer != null) return exoPlayer.getDuration();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getDuration();
        return -1;
    }

    public long getBuffered() {
        if (isExo() && exoPlayer != null) return exoPlayer.getBufferedPosition();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getBufferedPosition();
        return 0;
    }

    public long getBufferedAhead() {
        return Math.max(0, getBuffered() - getPosition());
    }

    public long getCachedBytes() {
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getCachedBytes();
        return 0;
    }

    public boolean isBuffering() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackState() == Player.STATE_BUFFERING;
        return buffering;
    }

    public boolean hasStartupBufferingProgress() {
        long currentPosition = Math.max(0, getPosition());
        long bufferedAhead = Math.max(0, getBuffered() - currentPosition);
        return currentPosition <= 0 && (bufferedAhead >= STARTUP_BUFFERED_AHEAD_THRESHOLD_MS || getCachedBytes() >= BUFFER_PROGRESS_RESET_THRESHOLD_BYTES);
    }

    private boolean haveDanmu() {
        return danmuView != null && danmuView.isPrepared();
    }

    public boolean canAdjustSpeed() {
        return isIjk() || !Setting.isTunnel();
    }

    public boolean haveTrack(int type) {
        if (isExo() && exoPlayer != null) return ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), type);
        if (isIjk() && ijkPlayer != null) return ijkPlayer.haveTrack(type);
        return false;
    }

    public boolean isPlaying() {
        return isExo() ? exoPlayer != null && exoPlayer.isPlaying() : ijkPlayer != null && ijkPlayer.isPlaying();
    }

    public boolean isEnd() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackState() == Player.STATE_ENDED;
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getPlaybackState() == IjkVideoView.STATE_ENDED;
        return false;
    }

    public boolean isRelease() {
        return exoPlayer == null || ijkPlayer == null;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        return getDuration() < 5 * 60 * 1000;
    }

    public boolean isVod() {
        return getDuration() > 5 * 60 * 1000;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        if (isExo() && exoPlayer != null) {
            VideoSize v = exoPlayer.getVideoSize();
            Format f = exoPlayer.getVideoFormat();
            String text = "dec " + v.width + "x" + v.height + " p" + String.format(Locale.getDefault(), "%.2f", v.pixelWidthHeightRatio);
            if (f != null) {
                text += " | src " + f.width + "x" + f.height + " p" + String.format(Locale.getDefault(), "%.2f", f.pixelWidthHeightRatio);
                if (f.rotationDegrees != 0) text += " r" + f.rotationDegrees;
            }
            return text;
        }
        return getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player)[player];
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        if (exoPlayer != null && !Setting.isTunnel()) exoPlayer.setPlaybackSpeed(speed);
        if (ijkPlayer != null) ijkPlayer.setSpeed(speed);
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.2f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        return setSpeed(speed);
    }

    public void togglePlayer() {
        setPlayer(isExo() ? SYS : ++player);
    }

    public void nextPlayer() {
        // 播放器优先级顺序：EXO硬解 -> EXO软解 -> IJK硬解 -> IJK软解 -> 系统
        if (player == EXO && decode == HARD) {
            // EXO硬解 -> EXO软解
            player = EXO;
            decode = SOFT;
        } else if (player == EXO && decode == SOFT) {
            // EXO软解 -> IJK硬解
            player = IJK;
            decode = HARD;
        } else if (player == IJK && decode == HARD) {
            // IJK硬解 -> IJK软解
            player = IJK;
            decode = SOFT;
        } else if (player == IJK && decode == SOFT) {
            // IJK软解 -> 系统
            player = SYS;
            decode = SOFT;
        } else {
            // 系统 或 其他情况 -> 重置为 EXO硬解
            player = EXO;
            decode = HARD;
        }
        setPlayer(player);
        this.decode = decode;
    }

    public void toggleDecode(boolean save) {
        applyDecode(isHard() ? SOFT : HARD, save);
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        time = sanitizeSeekPosition(time);
        // 标记seek操作，让同步器在seek后保持弹幕播放状态
        if (danmuSync != null) {
            danmuSync.markSeek();
        }
        // 先让视频播放器seek，然后弹幕再同步
        // 这样可以避免弹幕时间领先于视频时间
        boolean wasPlaying = isPlaying();
        if (isExo() && exoPlayer != null) seekExo(time, wasPlaying);
        if (isIjk() && ijkPlayer != null) ijkPlayer.seekTo(time);
        // 视频seek完成后，弹幕再跟随
        if (haveDanmu()) danmuView.seekTo(time);
        // seek操作后恢复弹幕的显示和播放状态
        // 解决拖动进度条后弹幕消失的问题
        if (haveDanmu() && Setting.isDanmu()) {
            danmuView.show();
            // 如果seek前视频正在播放，或者seek后视频正在播放，恢复弹幕播放
            if (wasPlaying || isPlaying()) {
                danmuView.resume();
            }
        }
    }

    private void seekExo(long time, boolean wasPlaying) {
        boolean wasEnded = exoPlayer.getPlaybackState() == Player.STATE_ENDED;
        startExoSeekFrameWatch(time);
        if (wasEnded || wasPlaying || exoPlayer.getPlayWhenReady()) exoPlayer.setPlayWhenReady(true);
        exoPlayer.seekTo(time);
    }

    private void startExoSeekFrameWatch(long time) {
        if (!hasExoVideoTrack()) return;
        exoAwaitingSeekFrame = true;
        exoPendingSeekPosition = time;
        exoSeekSurfaceRecoveryCount = 0;
        exoSeekGeneration++;
        App.removeCallbacks(seekFrameRunnable);
        App.post(seekFrameRunnable, EXO_SEEK_FRAME_TIMEOUT_MS);
    }

    private void clearExoSeekFrameWatch() {
        exoAwaitingSeekFrame = false;
        exoPendingSeekPosition = C.TIME_UNSET;
        exoSeekSurfaceRecoveryCount = 0;
        exoSeekGeneration++;
        App.removeCallbacks(seekFrameRunnable);
    }

    private boolean hasExoVideoTrack() {
        return exoPlayer != null && (ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), C.TRACK_TYPE_VIDEO) || exoPlayer.getVideoSize().width > 0 || exoPlayer.getVideoSize().height > 0);
    }

    private void checkExoSeekFrame() {
        if (!exoAwaitingSeekFrame || exoPlayer == null || !isExo()) return;
        if (!hasExoVideoTrack()) {
            clearExoSeekFrameWatch();
            return;
        }
        if (exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
            App.post(seekFrameRunnable, EXO_SEEK_FRAME_RECHECK_MS);
            return;
        }
        if (exoSeekSurfaceRecoveryCount++ >= EXO_SEEK_SURFACE_RECOVERY_LIMIT) {
            Logger.t(TAG).w("exo seek frame not rendered after recovery, position=" + exoPendingSeekPosition + ", state=" + exoPlayer.getPlaybackState());
            clearExoSeekFrameWatch();
            return;
        }
        long generation = exoSeekGeneration;
        long seekPosition = exoPendingSeekPosition;
        Logger.t(TAG).w("exo seek frame timeout, rebind surface, position=" + seekPosition + ", state=" + exoPlayer.getPlaybackState());
        rebindExoSurface();
        if (exoAwaitingSeekFrame && generation == exoSeekGeneration && exoPlayer != null && seekPosition != C.TIME_UNSET) {
            exoPlayer.seekTo(seekPosition);
            exoPlayer.setPlayWhenReady(true);
            App.post(seekFrameRunnable, EXO_SEEK_FRAME_RECHECK_MS);
        }
    }

    private void rebindExoSurface() {
        if (exoView == null || exoPlayer == null) return;
        ExoPlayer player = exoPlayer;
        runPlayerAction("rebindExoSurface", () -> {
            exoView.setPlayer(null);
            exoView.setPlayer(player);
        });
    }

    public void play() {
        if (isPlaying() || isEnd()) return;
        session.setActive(true);
        playbackLockManager.acquire();
        if (isExo()) playExo();
        if (isIjk()) playIjk();
        if (haveDanmu()) danmuView.resume();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (isExo()) pauseExo();
        if (isIjk()) pauseIjk();
        playbackLockManager.release();
        if (haveDanmu()) danmuView.pause();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        stopExo();
        stopIjk();
        clearBufferingWatchdog();
        playbackLockManager.release();
        session.setActive(false);
        if (haveDanmu()) danmuView.stop();
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
    }

    public void release() {
        stopParse();
        session.release();
        releaseExo();
        releaseIjk();
        exoView = null;
        ijkView = null;
        playbackLockManager.release();
        if (haveDanmu()) danmuView.release();
        removeTimeoutCheck();
        clearBufferingWatchdog();
        Server.get().setPlayer(null);
        App.execute(() -> Source.get().stop());
    }

    public void start(Channel channel, int timeout) {
        if (channel.hasMsg()) {
            ErrorEvent.extract(channel.getMsg());
        } else if (channel.getParse() == 1) {
            startParse(channel.result(), false);
        } else if (isIllegal(channel.getUrl())) {
            ErrorEvent.url(0);
        } else {
            setMediaSource(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        if (result.hasMsg()) {
            ErrorEvent.extract(result.getMsg());
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (isIllegal(result.getRealUrl())) {
            ErrorEvent.url(0);
        } else {
            setMediaSource(result, timeout);
        }
    }

    private void playExo() {
        if (exoPlayer == null) return;
        exoPlayer.play();
    }

    private void playIjk() {
        if (ijkPlayer == null) return;
        ijkPlayer.start();
    }

    private void pauseExo() {
        if (exoPlayer == null) return;
        exoPlayer.pause();
    }

    private void pauseIjk() {
        if (ijkPlayer == null) return;
        ijkPlayer.pause();
    }

    private void stopExo() {
        if (exoPlayer == null) return;
        clearExoSeekFrameWatch();
        runPlayerAction("stopExo", () -> {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        });
    }

    private void stopIjk() {
        if (ijkPlayer == null) return;
        runPlayerAction("stopIjk", ijkPlayer::stop);
    }

    private void releaseExo() {
        if (exoPlayer == null) return;
        clearExoSeekFrameWatch();
        ExoPlayer player = exoPlayer;
        exoPlayer = null;
        runPlayerAction("releaseExo", () -> {
            player.removeListener(this);
            if (exoView != null && exoView.getPlayer() == player) exoView.setPlayer(null);
            player.release();
            quitPlaybackThread();
        });
    }

    private void releaseIjk() {
        if (ijkPlayer == null) return;
        IjkVideoView player = ijkPlayer;
        ijkPlayer = null;
        runPlayerAction("releaseIjk", player::release);
    }

    private void quitPlaybackThread() {
        if (playbackThread == null) return;
        playbackThread.quit();
        playbackThread = null;
    }

    private void runPlayerAction(String action, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            Log.w(TAG, action + " failed", e);
        }
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public void setMediaSource() {
        setMediaSource(headers, url, format, drm, subs, Constant.TIMEOUT_PLAY);
    }

    public void setMediaSource(String url) {
        setMediaSource(new HashMap<>(), url);
    }

    public void continueLoadingGrace(long timeout) {
        if (isRelease()) return;
        prepareStartedAt = System.currentTimeMillis();
        removeTimeoutCheck();
        App.post(runnable, timeout);
        startBufferingWatchdog();
    }

    private void setMediaSource(Map<String, String> headers, String url) {
        setMediaSource(headers, url, null, null, new ArrayList<>(), Constant.TIMEOUT_PLAY);
    }

    private void setMediaSource(Channel channel, int timeout) {
        setMediaSource(channel.getHeaders(), channel.getUrl(), channel.getFormat(), channel.getDrm(), new ArrayList<>(), timeout);
    }

    private void setMediaSource(Result result, int timeout) {
        setMediaSource(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), timeout);
    }

    private void setMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout) {
        url = UrlUtil.toLocalhost(url);
        prepareStartedAt = System.currentTimeMillis();
        clearBufferingWatchdog();
        clearExoSeekFrameWatch();
        playbackLockManager.acquire();
        long startPosition = position == C.TIME_UNSET ? 0 : position;
        if (isIjk() && ijkPlayer != null) ijkPlayer.setMediaSource(IjkUtil.getSource(this.headers = checkUa(headers), this.url = url), startPosition);
        if (isExo() && exoPlayer != null) exoPlayer.setMediaItem(ExoUtil.getMediaItem(this.headers = checkUa(headers), UrlUtil.uri(this.url = url), this.format = format, this.drm = drm, checkSub(this.subs = subs), decode), startPosition);
        if (isExo() && exoPlayer != null) exoPlayer.prepare();
        App.post(runnable, getPrepareTimeout(timeout));
        PlayerEvent.prepare();
        Logger.t(TAG).d(url);
    }

    private long getPrepareTimeout(int timeout) {
        long prepareTimeout = timeout;
        if (isMobileMode()) {
            prepareTimeout = Math.max(timeout, 25_000L);
        }
        if (isLeanbackMode()) {
            prepareTimeout = Math.max(prepareTimeout, 35_000L);
        }
        if (isExo() && isLowPerformanceTv()) {
            prepareTimeout = Math.max(prepareTimeout, 45_000L);
        }
        return prepareTimeout;
    }

    private void logPrepareElapsed(String phase) {
        if (prepareStartedAt <= 0) return;
        long elapsed = System.currentTimeMillis() - prepareStartedAt;
        Logger.t(TAG).i("prepare " + phase + " elapsed=" + elapsed + "ms, player=" + player + ", decode=" + decode + ", armv7=" + ExoUtil.isArmeabiV7aOnly());
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    private void startBufferingWatchdog() {
        long now = System.currentTimeMillis();
        long currentPosition = getPosition();
        long currentBufferedPosition = getBuffered();
        long currentCachedBytes = getCachedBytes();
        if (!buffering || hasBufferingProgress(currentPosition, currentBufferedPosition, currentCachedBytes)) {
            bufferingStartedAt = now;
            bufferingStartPosition = currentPosition;
            bufferingStartBufferedPosition = currentBufferedPosition;
            bufferingStartCachedBytes = currentCachedBytes;
        }
        buffering = true;
        App.removeCallbacks(stallRunnable);
        App.post(stallRunnable, BUFFER_STALL_CHECK_INTERVAL);
    }

    private void clearBufferingWatchdog() {
        buffering = false;
        bufferingStartedAt = 0L;
        bufferingStartPosition = C.TIME_UNSET;
        bufferingStartBufferedPosition = C.TIME_UNSET;
        bufferingStartCachedBytes = 0L;
        App.removeCallbacks(stallRunnable);
    }

    private void checkPlaybackStall() {
        if (!buffering || isRelease()) return;
        long currentPosition = getPosition();
        long currentBufferedPosition = getBuffered();
        long currentCachedBytes = getCachedBytes();
        if (hasBufferingProgress(currentPosition, currentBufferedPosition, currentCachedBytes)) {
            bufferingStartedAt = System.currentTimeMillis();
            bufferingStartPosition = currentPosition;
            bufferingStartBufferedPosition = currentBufferedPosition;
            bufferingStartCachedBytes = currentCachedBytes;
            App.post(stallRunnable, BUFFER_STALL_CHECK_INTERVAL);
            return;
        }
        long elapsed = System.currentTimeMillis() - bufferingStartedAt;
        long bufferedAhead = Math.max(0, currentBufferedPosition - currentPosition);
        long stallTimeout = getBufferingTimeout(currentPosition, bufferedAhead);
        Logger.t(TAG).w("buffering stall elapsed=" + elapsed + "ms, timeout=" + stallTimeout + "ms, bufferedAhead=" + bufferedAhead + "ms, position=" + currentPosition + ", bufferedPos=" + currentBufferedPosition + ", cachedBytes=" + currentCachedBytes + ", url=" + url);
        if (elapsed >= stallTimeout) {
            clearBufferingWatchdog();
            ErrorEvent.timeout();
            return;
        }
        App.post(stallRunnable, BUFFER_STALL_CHECK_INTERVAL);
    }

    private boolean hasBufferingProgress(long currentPosition, long currentBufferedPosition, long currentCachedBytes) {
        if (bufferingStartPosition == C.TIME_UNSET) return true;
        if (Math.abs(currentPosition - bufferingStartPosition) > BUFFER_PROGRESS_RESET_THRESHOLD_MS) return true;
        if (bufferingStartBufferedPosition != C.TIME_UNSET && currentBufferedPosition - bufferingStartBufferedPosition > BUFFER_PROGRESS_RESET_THRESHOLD_MS) return true;
        return currentCachedBytes - bufferingStartCachedBytes > BUFFER_PROGRESS_RESET_THRESHOLD_BYTES;
    }

    private long getBufferingTimeout(long currentPosition, long bufferedAhead) {
        boolean mobileMode = isMobileMode();
        boolean leanbackMode = isLeanbackMode();
        boolean lowPerformanceTv = isLowPerformanceTv();
        if (currentPosition <= 0) {
            if (lowPerformanceTv) {
                return bufferedAhead >= 2_000L ? VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT : VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_STARTUP_TIMEOUT;
            }
            if (leanbackMode) {
                return bufferedAhead >= 2_000L ? LEANBACK_BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT : LEANBACK_BUFFER_STALL_STARTUP_TIMEOUT;
            }
            if (mobileMode) {
                return bufferedAhead >= 2_000L ? MOBILE_BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT : MOBILE_BUFFER_STALL_STARTUP_TIMEOUT;
            }
            return bufferedAhead >= 2_000L ? BUFFER_STALL_BUFFERED_STARTUP_TIMEOUT : BUFFER_STALL_STARTUP_TIMEOUT;
        }
        if (bufferedAhead <= 0) {
            if (lowPerformanceTv) return VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT;
            if (leanbackMode) return LEANBACK_BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT;
            if (mobileMode) return MOBILE_BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT;
            return BUFFER_STALL_EMPTY_PLAYBACK_TIMEOUT;
        }
        if (bufferedAhead < 2_000L) {
            if (lowPerformanceTv) return VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT;
            if (leanbackMode) return LEANBACK_BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT;
            if (mobileMode) return MOBILE_BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT;
            return BUFFER_STALL_LOW_BUFFER_PLAYBACK_TIMEOUT;
        }
        if (lowPerformanceTv) return VERY_LOW_PERFORMANCE_TV_BUFFER_STALL_PLAYBACK_TIMEOUT;
        if (leanbackMode) return LEANBACK_BUFFER_STALL_PLAYBACK_TIMEOUT;
        if (mobileMode) return MOBILE_BUFFER_STALL_PLAYBACK_TIMEOUT;
        return BUFFER_STALL_PLAYBACK_TIMEOUT;
    }

    private boolean isMobileMode() {
        return "mobile".equals(BuildConfig.FLAVOR_mode);
    }

    private boolean isLeanbackMode() {
        return "leanback".equals(BuildConfig.FLAVOR_mode);
    }

    private boolean isLowPerformanceTv() {
        return isLeanbackMode() && ExoUtil.isArmeabiV7aOnly();
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    private void setTrack(Track item) {
        if (item.isExo(player)) setTrackExo(item);
        if (item.isIjk(player)) setTrackIjk(item);
    }

    private void setTrackExo(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setTrackIjk(Track item) {
        if (item.isSelected()) {
            ijkPlayer.selectTrack(item.getType(), item.getTrack());
        } else {
            ijkPlayer.deselectTrack(item.getType(), item.getTrack());
        }
    }

    private void setPlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, getPosition(), getSpeed()).build());
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        if ("data".equals(scheme)) return false;
        return scheme.isEmpty() || "file".equals(scheme) ? !Path.exists(url) : host.isEmpty();
    }

    public static Map<String, String> checkUa(Map<String, String> headers) {
        if (Setting.getUa().isEmpty()) return headers;
        for (Map.Entry<String, String> header : headers.entrySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(header.getKey())) return headers;
        headers.put(HttpHeaders.USER_AGENT, Setting.getUa());
        return headers;
    }

    private List<Sub> checkSub(List<Sub> subs) {
        if (sub == null) return subs;
        subs.add(0, sub);
        return subs;
    }

    public Uri getUri() {
        return getUrl().startsWith("file://") || getUrl().startsWith("/") ? FileUtil.getShareUri(getUrl()) : Uri.parse(getUrl());
    }

    public String[] getHeaderArray() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : getHeaders().entrySet()) list.addAll(Arrays.asList(entry.getKey(), entry.getValue()));
        return list.toArray(new String[0]);
    }

    public Bundle getHeaderBundle() {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : getHeaders().entrySet()) bundle.putString(entry.getKey(), entry.getValue());
        return bundle;
    }

    private MediaMetadataCompat.Builder putBitmap(MediaMetadataCompat.Builder builder, Drawable drawable) {
        try {
            return builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, ((BitmapDrawable) drawable).getBitmap());
        } catch (Exception ignored) {
            return builder;
        }
    }

    public void setMetadata(String title, String artist, String artUri, Drawable drawable) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        session.setMetadata(putBitmap(builder, drawable).build());
        ActionEvent.update();
    }

    public void share(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, UrlUtil.fixDownloadUrl(getUrl()));
            intent.putExtra("extra_headers", getHeaderBundle());
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void choose(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(getUri(), "video/*");
            intent.putExtra("title", title);
            intent.putExtra("return_result", isVod());
            intent.putExtra("headers", getHeaderArray());
            if (isVod()) intent.putExtra("position", (int) getPosition());
            activity.startActivityForResult(Util.getChooser(intent), 1001);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkData(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) seekTo(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        setMediaSource(headers, url);
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (!events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_PLAYER_ERROR)) return;
        switch (player.getPlaybackState()) {
            case Player.STATE_IDLE:
                setPlaybackState(events.contains(Player.EVENT_PLAYER_ERROR) ? PlaybackStateCompat.STATE_ERROR : PlaybackStateCompat.STATE_NONE);
                break;
            case Player.STATE_READY:
                setPlaybackState(player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                break;
            case Player.STATE_BUFFERING:
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                break;
            case Player.STATE_ENDED:
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
        }
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
        setPlaybackState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        logPrepareElapsed("error");
        removeTimeoutCheck();
        clearBufferingWatchdog();
        clearExoSeekFrameWatch();
        playbackLockManager.release();
        Logger.t(TAG).e(error.errorCode + "," + url);
        ErrorEvent.url(ExoUtil.getRetry(error.errorCode), error.errorCode);
    }

    @Override
    public void onRenderedFirstFrame() {
        if (exoAwaitingSeekFrame) Logger.t(TAG).i("exo seek frame rendered, position=" + getPosition());
        clearExoSeekFrameWatch();
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize size) {
        Logger.t(TAG).i("videoSize=%dx%d par=%.4f rotation=%d url=%s", size.width, size.height, size.pixelWidthHeightRatio, size.unappliedRotationDegrees, url);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (isExo() && exoPlayer != null && (state == Player.STATE_BUFFERING || state == Player.STATE_READY)) {
            long bufferedAheadMs = Math.max(0, exoPlayer.getBufferedPosition() - exoPlayer.getCurrentPosition());
            Logger.t(TAG).i("exo state=" + state + ", bufferedAhead=" + bufferedAheadMs + "ms, bufferedPos=" + exoPlayer.getBufferedPosition() + ", currentPos=" + exoPlayer.getCurrentPosition());
        }
        if (state == Player.STATE_BUFFERING) startBufferingWatchdog();
        else clearBufferingWatchdog();
        if (state == Player.STATE_READY) {
            logPrepareElapsed("ready");
            removeTimeoutCheck();
        }
        PlayerEvent.state(state);
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                startBufferingWatchdog();
                PlayerEvent.state(Player.STATE_BUFFERING);
                break;
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
            case IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START:
                clearBufferingWatchdog();
                PlayerEvent.state(Player.STATE_READY);
                break;
        }
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        logPrepareElapsed("ijk_error");
        removeTimeoutCheck();
        clearBufferingWatchdog();
        playbackLockManager.release();
        setPlaybackState(PlaybackStateCompat.STATE_ERROR);
        ErrorEvent.url(1);
        return true;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        logPrepareElapsed("ijk_ready");
        removeTimeoutCheck();
        clearBufferingWatchdog();
        PlayerEvent.state(Player.STATE_READY);
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        clearBufferingWatchdog();
        playbackLockManager.release();
        PlayerEvent.state(Player.STATE_ENDED);
    }

    private long sanitizeSeekPosition(long time) {
        long duration = getDuration();
        if (duration > 0 && time > duration) return duration;
        return Math.max(0, time);
    }

    @Override
    public void prepared() {
        App.post(() -> {
            if (danmuView == null) return;
            if (isPlaying() && danmuView.isPrepared()) danmuView.start(getPosition());
            if (Setting.isDanmu()) danmuView.show();
            else danmuView.hide();
        });
    }

    @Override
    public void updateTimer(DanmakuTimer timer) {

    }

    @Override
    public void danmakuShown(BaseDanmaku danmaku) {
    }

    @Override
    public void drawingFinished() {
    }

    private static class DroppedFrameListener implements AnalyticsListener {
        @Override
        public void onDroppedVideoFrames(@NonNull EventTime eventTime, int droppedFrames, long elapsedMs) {
            Logger.w("Dropped %d video frames in %dms (position=%dms)", droppedFrames, elapsedMs, eventTime.eventPlaybackPositionMs);
        }
    }
}
