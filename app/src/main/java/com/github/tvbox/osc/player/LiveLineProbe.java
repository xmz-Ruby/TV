package com.github.tvbox.osc.player;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import com.github.tvbox.osc.App;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.bean.Channel;
import com.github.tvbox.osc.bean.Sub;
import com.github.tvbox.osc.player.exo.ExoUtil;
import com.github.tvbox.osc.utils.UrlUtil;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiveLineProbe {

    private static final String TAG = LiveLineProbe.class.getSimpleName();
    private static final long PROBE_TIMEOUT_MS = 15_000L;

    private final Handler handler;
    private final List<Integer> pending;

    private ExecutorService executor;
    private Callback callback;
    private ExoPlayer player;
    private Channel source;
    private Runnable timeoutRunnable;
    private boolean stopped;
    private int currentProbeLine;
    private int bestHeight;
    private int bestLine;
    private int bestWidth;
    private int token;

    public LiveLineProbe() {
        this.handler = new Handler(Looper.getMainLooper());
        this.pending = new ArrayList<>();
        this.bestLine = -1;
        this.currentProbeLine = -1;
    }

    public void start(Channel channel, int currentLine, int width, int height, int token, Callback callback) {
        stop();
        if (channel == null || channel.getUrls().size() <= 1) return;
        this.executor = Executors.newSingleThreadExecutor();
        this.callback = callback;
        this.source = channel;
        this.token = token;
        this.bestLine = currentLine;
        this.bestWidth = Math.max(width, 0);
        this.bestHeight = Math.max(height, 0);
        this.stopped = false;
        this.pending.clear();
        for (int i = 0; i < channel.getUrls().size(); i++) if (i != currentLine) pending.add(i);
        probeNext();
    }

    public boolean isRunning(int token, Channel channel) {
        return !stopped && this.token == token && this.source == channel && (!pending.isEmpty() || currentProbeLine != -1);
    }

    public void updateBaseline(int line, int width, int height) {
        long currentScore = score(width, height);
        if (currentScore < score(bestWidth, bestHeight)) return;
        bestLine = line;
        bestWidth = Math.max(width, 0);
        bestHeight = Math.max(height, 0);
    }

    public void stop() {
        stopped = true;
        pending.clear();
        currentProbeLine = -1;
        removeTimeout();
        releasePlayer();
        if (executor != null) executor.shutdownNow();
        executor = null;
        callback = null;
        source = null;
    }

    private void probeNext() {
        if (stopped) return;
        if (pending.isEmpty()) {
            finish();
            return;
        }
        currentProbeLine = pending.remove(0);
        ExecutorService service = executor;
        if (service == null) return;
        service.execute(() -> {
            Channel probe = buildProbeChannel(currentProbeLine);
            if (probe == null) {
                handler.post(this::failCurrent);
                return;
            }
            handler.post(() -> prepare(probe));
        });
    }

    private Channel buildProbeChannel(int line) {
        try {
            if (stopped || source == null || line >= source.getUrls().size()) return null;
            Channel probe = Channel.create(source);
            probe.setLine(line);
            probe.setMsg(null);
            probe.setUrl(Source.get().fetch(probe));
            if (probe.hasMsg() || probe.getParse() == 1 || TextUtils.isEmpty(probe.getUrl())) return null;
            return probe;
        } catch (Throwable e) {
            Logger.t(TAG).w("probe fetch failed line=" + line + ", " + e.getMessage());
            return null;
        }
    }

    private void prepare(Channel channel) {
        if (stopped) return;
        releasePlayer();
        try {
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
            trackSelector.setParameters(trackSelector.buildUponParameters().setForceHighestSupportedBitrate(true));
            player = new ExoPlayer.Builder(App.get())
                    .setLoadControl(ExoUtil.buildLoadControl())
                    .setTrackSelector(trackSelector)
                    .setRenderersFactory(ExoUtil.buildRenderersFactory(Setting.getDecode(Players.EXO), Setting.isAudioDownmix(), false))
                    .setMediaSourceFactory(ExoUtil.buildMediaSourceFactory())
                    .build();
            player.setVolume(0f);
            player.setPlayWhenReady(true);
            player.addListener(new Listener());
            player.setMediaItem(ExoUtil.getMediaItem(
                    Players.checkUa(new HashMap<>(channel.getHeaders())),
                    UrlUtil.uri(channel.getUrl()),
                    channel.getFormat(),
                    channel.getDrm(),
                    new ArrayList<Sub>(),
                    Setting.getDecode(Players.EXO)
            ));
            player.prepare();
            startTimeout();
        } catch (Throwable e) {
            Logger.t(TAG).w("probe prepare failed line=" + currentProbeLine + ", " + e.getMessage());
            failCurrent();
        }
    }

    private void startTimeout() {
        removeTimeout();
        timeoutRunnable = this::failCurrent;
        handler.postDelayed(timeoutRunnable, PROBE_TIMEOUT_MS);
    }

    private void removeTimeout() {
        if (timeoutRunnable == null) return;
        handler.removeCallbacks(timeoutRunnable);
        timeoutRunnable = null;
    }

    private void completeCurrent() {
        if (stopped) return;
        removeTimeout();
        releasePlayer();
        currentProbeLine = -1;
        probeNext();
    }

    private void failCurrent() {
        if (stopped) return;
        Callback cb = callback;
        Channel item = source;
        int line = currentProbeLine;
        int currentToken = token;
        if (cb != null && item != null && line != -1) cb.onLineFailed(item, line, currentToken);
        completeCurrent();
    }

    private void finish() {
        Callback cb = callback;
        Channel item = source;
        int currentToken = token;
        stop();
        if (cb != null && item != null) cb.onFinished(item, currentToken);
    }

    private void releasePlayer() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::releasePlayer);
            return;
        }
        if (player == null) return;
        try {
            player.release();
        } catch (Throwable e) {
            Logger.t(TAG).w("probe release failed: " + e.getMessage());
        }
        player = null;
    }

    private void onReady() {
        if (stopped || player == null) return;
        Size size = getBestSize(player);
        if (!size.isValid()) {
            failCurrent();
            return;
        }
        Callback cb = callback;
        Channel item = source;
        int line = currentProbeLine;
        int currentToken = token;
        if (cb != null && item != null) cb.onLineReady(item, line, size.width, size.height, currentToken);
        if (isBetter(size.width, size.height, bestWidth, bestHeight)) {
            bestLine = currentProbeLine;
            bestWidth = size.width;
            bestHeight = size.height;
            if (cb != null && item != null) cb.onBetterLine(item, line, size.width, size.height, currentToken);
        }
        completeCurrent();
    }

    private Size getBestSize(ExoPlayer player) {
        Size best = new Size(player.getVideoSize().width, player.getVideoSize().height);
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format format = group.getTrackFormat(i);
                if (format.width == Format.NO_VALUE || format.height == Format.NO_VALUE) continue;
                if (isBetter(format.width, format.height, best.width, best.height)) {
                    best = new Size(format.width, format.height);
                }
            }
        }
        return best;
    }

    private boolean isBetter(int width, int height, int currentWidth, int currentHeight) {
        long score = score(width, height);
        long currentScore = score(currentWidth, currentHeight);
        return score > currentScore || score == currentScore && width > currentWidth;
    }

    private long score(int width, int height) {
        return Math.max(width, 0L) * Math.max(height, 0L);
    }

    private final class Listener implements Player.Listener {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) onReady();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Logger.t(TAG).w("probe player error line=" + currentProbeLine + ", code=" + error.errorCode);
            failCurrent();
        }
    }

    private static final class Size {
        private final int width;
        private final int height;

        private Size(int width, int height) {
            this.width = Math.max(width, 0);
            this.height = Math.max(height, 0);
        }

        private boolean isValid() {
            return width > 0 && height > 0;
        }
    }

    public interface Callback {
        void onLineReady(Channel channel, int line, int width, int height, int token);

        void onLineFailed(Channel channel, int line, int token);

        void onBetterLine(Channel channel, int line, int width, int height, int token);

        void onFinished(Channel channel, int token);
    }
}
