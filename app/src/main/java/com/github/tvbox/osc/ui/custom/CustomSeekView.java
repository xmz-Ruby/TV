package com.github.tvbox.osc.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.player.Players;
import com.github.tvbox.osc.player.exo.ExoUtil;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

public class CustomSeekView extends FrameLayout implements TimeBar.OnScrubListener {

    private static final int MAX_UPDATE_INTERVAL_MS = 1000;
    private static final int MIN_UPDATE_INTERVAL_MS = 200;
    // 估算视频码率用于计算内存使用（默认5Mbps，会根据实际情况调整）
    private static final int DEFAULT_BITRATE_KBPS = 5000;
    // 音频码率约128Kbps
    private static final int AUDIO_BITRATE_KBPS = 128;
    private static final long MAX_ESTIMATED_BUFFER_BYTES = ExoUtil.MAX_TARGET_BUFFER_BYTES;

    private TextView positionView;
    private TextView durationView;
    private TextView bufferedView;
    private DefaultTimeBar timeBar;

    private Runnable refresh;
    private Players player;
    private OnSeekListener seekListener;

    private long currentDuration;
    private long currentPosition;
    private long currentBuffered;
    private boolean scrubbing;

    public interface OnSeekListener {
        void onSeekStart();
        void onSeekComplete(long position);
    }

    public CustomSeekView(Context context) {
        this(context, null);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_control_seek, this);
        init();
        start();
    }

    private void init() {
        positionView = findViewById(R.id.position);
        durationView = findViewById(R.id.duration);
        bufferedView = findViewById(R.id.buffered);
        timeBar = findViewById(R.id.timeBar);
        timeBar.addListener(this);
        refresh = this::refresh;
    }

    public void setListener(Players player) {
        this.player = player;
    }

    public void setSeekListener(OnSeekListener listener) {
        this.seekListener = listener;
    }

    private void start() {
        removeCallbacks(refresh);
        post(refresh);
    }

    private String formatBufferSize(long cachedBytes) {
        if (cachedBytes <= 0) {
            // 如果无法获取实际缓存字节数，返回估算值
            return "";
        }
        if (cachedBytes < 1024 * 1024) {
            return (cachedBytes / 1024) + "KB";
        } else if (cachedBytes < 1024 * 1024 * 1024) {
            return (cachedBytes / (1024 * 1024)) + "MB";
        } else {
            DecimalFormat df = new DecimalFormat("#.#");
            return df.format(cachedBytes / (1024.0 * 1024 * 1024)) + "GB";
        }
    }

    private String formatBufferSizeEstimate(long bufferedMs) {
        if (bufferedMs <= 0) {
            return "";
        }
        // 估算缓冲大小（视频+音频）- 仅用于无法获取实际缓存字节数时
        int avgBitrateKbps = DEFAULT_BITRATE_KBPS + AUDIO_BITRATE_KBPS;
        long bufferedSeconds = bufferedMs / 1000;
        long bufferSizeBytes = Math.min(MAX_ESTIMATED_BUFFER_BYTES, bufferedSeconds * avgBitrateKbps * 1024L / 8);

        if (bufferSizeBytes < 1024 * 1024) {
            return (bufferSizeBytes / 1024) + "KB";
        } else if (bufferSizeBytes < 1024 * 1024 * 1024) {
            return (bufferSizeBytes / (1024 * 1024)) + "MB";
        } else {
            DecimalFormat df = new DecimalFormat("#.#");
            return df.format(bufferSizeBytes / (1024.0 * 1024 * 1024)) + "GB";
        }
    }

    private void refresh() {
        if (player.isRelease()) return;
        long duration = player.getDuration();
        long position = player.getPosition();
        long buffered = player.getBuffered();
        boolean positionChanged = position != currentPosition;
        boolean durationChanged = duration != currentDuration;
        currentDuration = duration;
        currentPosition = position;
        currentBuffered = buffered;

        if (durationChanged) {
            setKeyTimeIncrement(duration);
            timeBar.setDuration(duration);
            durationView.setText(player.stringToTime(duration < 0 ? 0 : duration));
        }
        if (positionChanged && !scrubbing) {
            timeBar.setPosition(position);
            positionView.setText(player.stringToTime(position < 0 ? 0 : position));
        }
        long bufferedPosition = duration > 0 ? Math.min(buffered, duration) : buffered;
        long bufferedAhead = Math.max(0, bufferedPosition - Math.max(position, 0));
        // 始终更新缓冲显示，确保暂停时也能看到缓冲状态
        timeBar.setBufferedPosition(bufferedPosition);
        if (bufferedAhead > 0) {
            String bufferedTime = player.stringToTime(bufferedAhead);
            // 优先使用实际缓存字节数（IjkPlayer）
            long cachedBytes = player.getCachedBytes();
            String bufferSize;
            if (cachedBytes > 0) {
                bufferSize = formatBufferSize(cachedBytes);
            } else {
                // Exo 只能近似估算当前向前缓冲的数据量。
                bufferSize = formatBufferSizeEstimate(bufferedAhead);
            }
            if (!bufferSize.isEmpty()) {
                bufferedView.setText(bufferedTime + " " + bufferSize);
                bufferedView.setVisibility(VISIBLE);
            } else {
                bufferedView.setText(bufferedTime);
                bufferedView.setVisibility(VISIBLE);
            }
        } else {
            bufferedView.setVisibility(INVISIBLE);
        }
        if (player.isEmpty()) {
            positionView.setText("00:00");
            durationView.setText("00:00");
            bufferedView.setVisibility(INVISIBLE);
            timeBar.setPosition(currentDuration = 0);
            timeBar.setDuration(currentDuration = 0);
            timeBar.setBufferedPosition(0);
        }
        removeCallbacks(refresh);
        if (player.isPlaying()) {
            postDelayed(refresh, delayMs(position));
        } else {
            postDelayed(refresh, MAX_UPDATE_INTERVAL_MS);
        }
    }

    private void setKeyTimeIncrement(long duration) {
        if (duration > TimeUnit.HOURS.toMillis(2)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(5));
        } else if (duration > TimeUnit.HOURS.toMillis(1)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(3));
        } else if (duration > TimeUnit.MINUTES.toMillis(30)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
        } else if (duration > TimeUnit.MINUTES.toMillis(15)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(30));
        } else if (duration > TimeUnit.MINUTES.toMillis(10)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(15));
        } else if (duration > TimeUnit.MINUTES.toMillis(5)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(10));
        } else if (duration > 0) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(5));
        }
    }

    private long delayMs(long position) {
        long mediaTimeUntilNextFullSecondMs = 1000 - position % 1000;
        long mediaTimeDelayMs = Math.min(timeBar.getPreferredUpdateDelay(), mediaTimeUntilNextFullSecondMs);
        long delayMs = (long) (mediaTimeDelayMs / player.getSpeed());
        return Util.constrainValue(delayMs, MIN_UPDATE_INTERVAL_MS, MAX_UPDATE_INTERVAL_MS);
    }

    private void seekToTimeBarPosition(long positionMs) {
        player.seekTo(positionMs);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(refresh);
    }

    @Override
    public void onScrubStart(@NonNull TimeBar timeBar, long position) {
        scrubbing = true;
        positionView.setText(player.stringToTime(position));
        if (seekListener != null) {
            seekListener.onSeekStart();
        }
    }

    @Override
    public void onScrubMove(@NonNull TimeBar timeBar, long position) {
        positionView.setText(player.stringToTime(position));
    }

    @Override
    public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
        scrubbing = false;
        if (!canceled) {
            seekToTimeBarPosition(position);
            if (seekListener != null) {
                seekListener.onSeekComplete(position);
            }
        }
    }
}
